package com.ebremer.halcyon.fuseki;


/**
 *
 * @author erich
 */
public class HalcyonSecurityHandler {//extends SecurityHandler {
    
    public HalcyonSecurityHandler() {
        
    }
/*
    @Override
    protected RoleInfo prepareConstraintInfo(String string, Request rqst) {
        System.out.println("prepareConstraintInfo");
        RoleInfo ri = new RoleInfo();
        return ri;
    }

    @Override
    protected boolean checkUserDataPermissions(String string, Request rqst, Response rspns, RoleInfo ri) throws IOException {
        System.out.println("checkUserDataPermissions");       
        System.out.println("YES!!!!! ---> "+rqst.getHeader(AddParamsToHeader.HID));
        Subject s = SecurityUtils.getSubject();
        return true;
    }

    @Override
    protected boolean isAuthMandatory(Request rqst, Response rspns, Object o) {
        System.out.println("isAuthMandatory");
        return false;
    }

    @Override
    protected boolean checkWebResourcePermissions(String string, Request rqst, Response rspns, Object o, UserIdentity ui) throws IOException {
        System.out.println("checkWebResourcePermissions");
        return true;
    }

    @Override
    protected Constraint getConstraint(String string, Request rqst) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
*/
}
