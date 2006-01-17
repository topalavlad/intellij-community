package com.intellij.codeInsight.generation;

import com.intellij.j2ee.ejb.EjbRolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbClassRoleEnum;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.javaee.model.common.EntityBean;
import com.intellij.javaee.model.common.CmpField;

import java.util.ArrayList;

abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateGetterSetterHandlerBase");

  public GenerateGetterSetterHandlerBase(String chooserTitle) {
    super(chooserTitle);
  }

  protected Object[] getAllOriginalMembers(PsiClass aClass) {
    ArrayList<Object> array = new ArrayList<Object>();

    try{
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (generateMemberPrototypes(aClass, field).length > 0) {
          array.add(field);
        }
      }

      getCmpFields(array, aClass);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    return array.toArray(new Object[array.size()]);
  }

  private void getCmpFields(ArrayList<Object> list, PsiClass psiClass) throws IncorrectOperationException {
    final EjbClassRole classRole = EjbRolesUtil.getEjbRole(psiClass);
    if (classRole == null || classRole.getType() != EjbClassRoleEnum.EJB_CLASS_ROLE_EJB_CLASS) return;
    if (!EjbUtil.isCMP2x(classRole.getEnterpriseBean())) return;


    for (final CmpField field : ((EntityBean)classRole.getEnterpriseBean()).getCmpFields()) {
      if (generateMemberPrototypes(psiClass, field).length > 0) {
        list.add(field);
      }
    }
  }

}