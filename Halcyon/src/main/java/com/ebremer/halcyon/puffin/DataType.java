package com.ebremer.halcyon.puffin;

import com.ebremer.ns.HAL;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author erich
 */
public enum DataType {
    STRING(XSD.xstring.getURI(), java.lang.String.class),
    INTEGER(XSD.integer.getURI(), java.lang.Integer.class),
    BOOLEAN(XSD.xboolean.getURI(), java.lang.Boolean.class),
    FLOAT(XSD.xfloat.getURI(), java.lang.Float.class),
    DOUBLE(XSD.xdouble.getURI(), java.lang.Double.class),
    FORM(HAL.SHACLForm.getURI(), SHACLForm.class),
    BNODE(SHACLM.BlankNode.getURI(), org.apache.jena.rdf.model.Resource.class),
    IRI(SHACLM.IRI.getURI(), org.apache.jena.rdf.model.Resource.class),
    RESOURCE(null, org.apache.jena.rdf.model.Resource.class);

    private final String xsdType;
    private final Class<?> javaType;

    DataType(String xsdType, Class<?> javaType) {
        this.xsdType = xsdType;
        this.javaType = javaType;
    }

    public String getXsdType() {
        return this.xsdType;
    }

    public Class<?> getJavaType() {
        return this.javaType;
    }
    
    public static DataType fromXsdType(String xsdType) {
        for (DataType dataType : DataType.values()) {
            if (dataType.getXsdType().equals(xsdType)) {
                return dataType;
            }
        }
        throw new IllegalArgumentException("No enum constant for XSD type: " + xsdType);
    }

    public static DataType fromJavaType(Class<?> javaType) {
        for (DataType dataType : DataType.values()) {
            if (dataType.getJavaType().equals(javaType)) {
                return dataType;
            }
        }
        throw new IllegalArgumentException("No enum constant for Java type: " + javaType);
    }
    
    public static Class<?> xsdToJavaType(String xsdType) {
        return fromXsdType(xsdType).getJavaType();
    }
    
    public static void main(String[] args) {
        DataType dtFromXsd = DataType.fromXsdType(XSD.xstring.getURI());
        System.out.println(dtFromXsd);

        DataType dtFromJava = DataType.fromJavaType(java.lang.Integer.class);
        System.out.println(dtFromJava);
        
        Class<?> javaType = DataType.xsdToJavaType(XSD.integer.getURI());
        System.out.println(javaType);
    }
}
