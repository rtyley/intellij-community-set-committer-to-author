package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import groovy.lang.Closure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class CustomMembersGenerator implements GdslMembersHolderConsumer {
  private final Set<Map> myMethods = new HashSet<Map>();
  private final Project myProject;
  private final PsiElement myPlace;
  private final String myQualifiedName;
  private final CompoundMembersHolder myDepot = new CompoundMembersHolder();

  public CustomMembersGenerator(Project project, PsiElement place, String qualifiedName) {
    myProject = project;
    myPlace = place;
    myQualifiedName = qualifiedName;
  }

  public PsiElement getPlace() {
    return myPlace;
  }

  @Nullable
  public PsiClass getClassType() {
    return JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, myPlace.getResolveScope());
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public CustomMembersHolder getMembersHolder() {
    // Add non-code members holder
    if (!myMethods.isEmpty()) {
      addMemberHolder(NonCodeMembersHolder.generateMembers(myMethods, myPlace.getContainingFile()));
    }
    return myDepot;
  }

  public void addMemberHolder(CustomMembersHolder holder) {
    myDepot.addHolder(holder);
  }

  protected Object[] constructNewArgs(Object[] args) {
    final Object[] newArgs = new Object[args.length + 1];
    //noinspection ManualArrayCopy
    for (int i = 0; i < args.length; i++) {
      newArgs[i] = args[i];
    }
    newArgs[args.length] = this;
    return newArgs;
  }


  /** **********************************************************
   Methods to add new behavior
   *********************************************************** */
  public void property(Map<Object, Object> args) {
    String name = (String)args.get("name");
    Object type = args.get("type");
    Boolean isStatic = (Boolean)args.get("isStatic");

    Map<Object, Object> getter = new HashMap<Object, Object>();
    getter.put("name", GroovyPropertyUtils.getGetterNameNonBoolean(name));
    getter.put("type", type);
    getter.put("isStatic", isStatic);
    method(getter);

    Map<Object, Object> setter = new HashMap<Object, Object>();
    setter.put("name", GroovyPropertyUtils.getSetterName(name));
    setter.put("type", "void");
    setter.put("isStatic", isStatic);
    final HashMap<Object, Object> param = new HashMap<Object, Object>();
    param.put(name, type);
    setter.put("params", param);
    method(setter);
  }

  public void method(Map<Object, Object> args) {
    args.put("type", stringifyType(args.get("type")));
    //noinspection unchecked
    final Map<Object, Object> params = (Map)args.get("params");
    if (params != null) {
      for (Map.Entry<Object, Object> entry : params.entrySet()) {
        entry.setValue(stringifyType(entry.getValue()));
      }
    }
    myMethods.add(args);
  }

  private static String stringifyType(Object type) {
    return type instanceof Closure ? "groovy.lang.Closure" :
    type instanceof Map ? "java.util.Map" :
    type instanceof Class ? ((Class)type).getName() :
    type != null ? type.toString() : "java.lang.Object";
  }

}
