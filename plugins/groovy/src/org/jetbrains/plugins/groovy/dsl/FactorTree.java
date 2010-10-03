package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;

import java.util.Map;

/**
 * @author peter
 */
public class FactorTree {
  private static final Object ourHolderKey = new Object();
  private final Map myCache = new ConcurrentHashMap();

  public void cache(GroovyClassDescriptor descriptor, CustomMembersHolder holder) {
    Map current = myCache;
    for (Factor factor : descriptor.affectingFactors) {
      Object key;
      switch (factor) {
        case placeElement: key = descriptor.getPlace(); break;
        //case placeFile: key = descriptor.getPlaceFile(); break;
        case qualifierType: key = descriptor.getPsiType().getCanonicalText(); break;
        default: throw new IllegalStateException("Unknown variant: "+ factor);
      }
      Map next = (Map)current.get(key);
      if (next == null) current.put(key, next = new ConcurrentHashMap());
      current = next;
    }

    current.put(ourHolderKey, holder);
  }

  @Nullable
  public CustomMembersHolder retrieve(PsiElement place, String qualifierType) {
    return retrieveImpl(place, qualifierType, myCache);

  }

  @Nullable
  private static CustomMembersHolder retrieveImpl(@NotNull PsiElement place,
                                                  @NotNull String qualifierType, @Nullable Map current) {
    if (current == null) return null;

    CustomMembersHolder result;

    result = (CustomMembersHolder)current.get(ourHolderKey);
    if (result != null) return result;

    result = retrieveImpl(place, qualifierType, (Map)current.get(qualifierType));
    if (result != null) return result;

    /*
    result = retrieveImpl(place, placeFile, qualifierType, (Map)current.get(placeFile));
    if (result != null) return result;

    */
    return retrieveImpl(place, qualifierType, (Map)current.get(place));
  }

}

enum Factor {
  qualifierType, placeElement//, placeFile
}
