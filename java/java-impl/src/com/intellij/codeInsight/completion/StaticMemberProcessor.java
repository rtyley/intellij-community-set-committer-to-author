package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.CollectionFactory.hashSet;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

/**
* @author peter
*/
public abstract class StaticMemberProcessor {
  private final Set<PsiClass> myStaticImportedClasses = hashSet();
  private final PsiElement myPosition;
  private final Project myProject;
  private final PsiResolveHelper myResolveHelper;
  private boolean myHintShown = false;
  private final boolean myPackagedContext;

  public StaticMemberProcessor(final PsiElement position) {
    myPosition = position;
    myProject = myPosition.getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    myPackagedContext = JavaCompletionUtil.inSomePackage(position);
  }

  public void importMembersOf(@Nullable PsiClass psiClass) {
    addIfNotNull(myStaticImportedClasses, psiClass);
  }

  public void processStaticMethodsGlobally(final PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

    Comparator<String> comparator = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        boolean start1 = matcher.isStartMatch(o1);
        boolean start2 = matcher.isStartMatch(o1);
        return start1 == start2 ? 0 : start2 ? -1 : 1;
      }
    };

    final GlobalSearchScope scope = myPosition.getResolveScope();
    final PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(myProject);
    String[] methodNames = namesCache.getAllMethodNames();
    Arrays.sort(methodNames, comparator);
    for (final String methodName : methodNames) {
      if (matcher.prefixMatches(methodName)) {
        Set<PsiClass> classes = new THashSet<PsiClass>();
        for (final PsiMethod method : namesCache.getMethodsByName(methodName, scope)) {
          if (isStaticallyImportable(method)) {
            final PsiClass containingClass = method.getContainingClass();
            assert containingClass != null : method.getName() + "; " + method + "; " + method.getClass();

            if (classes.add(containingClass) && JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
              final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
              showHint(shouldImport);

              final PsiMethod[] allMethods = containingClass.getAllMethods();
              final List<PsiMethod> overloads = ContainerUtil.findAll(allMethods, new Condition<PsiMethod>() {
                @Override
                public boolean value(PsiMethod psiMethod) {
                  return methodName.equals(psiMethod.getName()) && isStaticallyImportable(psiMethod);
                }
              });

              assert !overloads.isEmpty();
              if (overloads.size() == 1) {
                assert method == overloads.get(0);
                consumer.consume(createLookupElement(method, containingClass, shouldImport));
              } else {
                if (overloads.get(0).getParameterList().getParametersCount() == 0) {
                  overloads.add(0, overloads.remove(1));
                }
                consumer.consume(createLookupElement(overloads, containingClass, shouldImport));
              }
            }
          }
        }
      }
    }
    String[] fieldNames = namesCache.getAllFieldNames();
    Arrays.sort(fieldNames, comparator);
    for (final String fieldName : fieldNames) {
      if (matcher.prefixMatches(fieldName)) {
        for (final PsiField field : namesCache.getFieldsByName(fieldName, scope)) {
          if (isStaticallyImportable(field)) {
            final PsiClass containingClass = field.getContainingClass();
            assert containingClass != null : field.getName() + "; " + field + "; " + field.getClass();

            if (JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
              final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
              showHint(shouldImport);
              consumer.consume(createLookupElement(field, containingClass, shouldImport));
            }
          }
        }
      }
    }
  }

  private void showHint(boolean shouldImport) {
    if (!myHintShown && !shouldImport && CompletionService.getCompletionService().getAdvertisementText() == null) {
      final String shortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      if (shortcut != null) {
        CompletionService.getCompletionService().setAdvertisementText("To import a method statically, press " + shortcut);
      }
      myHintShown = true;
    }
  }

  public List<PsiMember> processMembersOfRegisteredClasses(final PrefixMatcher matcher, PairConsumer<PsiMember, PsiClass> consumer) {
    final ArrayList<PsiMember> result = CollectionFactory.arrayList();
    for (final PsiClass psiClass : myStaticImportedClasses) {
      for (final PsiMethod method : psiClass.getAllMethods()) {
        if (matcher.prefixMatches(method.getName())) {
          if (isStaticallyImportable(method)) {
            consumer.consume(method, psiClass);
          }
        }
      }
      for (final PsiField field : psiClass.getAllFields()) {
        if (matcher.prefixMatches(field. getName())) {
          if (isStaticallyImportable(field)) {
            consumer.consume(field, psiClass);
          }
        }
      }
    }
    return result;
  }


  private boolean isStaticallyImportable(final PsiMember member) {
    return member.hasModifierProperty(PsiModifier.STATIC) && isAccessible(member) && !StaticImportMethodFix.isExcluded(member);
  }

  protected boolean isAccessible(PsiMember member) {
    return myResolveHelper.isAccessible(member, myPosition, null);
  }

  @NotNull
  protected abstract LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport);

  protected abstract LookupElement createLookupElement(@NotNull List<PsiMethod> overloads, @NotNull PsiClass containingClass, boolean shouldImport);
}
