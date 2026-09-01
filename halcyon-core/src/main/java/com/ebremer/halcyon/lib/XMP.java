package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import static org.apache.commons.codec.binary.Base64.encodeBase64;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.JenaXMLInput;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RDFWriterBuilder;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class XMP {
    private static final Logger logger = LoggerFactory.getLogger(XMP.class);
    private BigDecimal magnification = null;
    private BigDecimal ppsx = null;
    private BigDecimal ppsy = null;
    private final String uuid;
    private String manufacturer = null;
    private String manufacturerdevicename = null;
    private byte[] iccprofile = null;
    private String ImageComments = null;
    private BigDecimal exposuretime = null;
    
    public XMP() {
        this.uuid = "https://dummyabcde.com/"+UUID.randomUUID().toString();
    }
    
    public String getUUID() {
        return uuid;
    }
    
    public void setMagnification(BigDecimal magnification) {        
        this.magnification = magnification;
    }
    
    public void setExposureTime(BigDecimal s) {        
        this.exposuretime = s;
    }
    
    public void setManufacturer(String s) {        
        this.manufacturer = s;
    }

    public void setImageComments(String s) {        
        this.ImageComments = s;
    }
    
    public void setICCColorProfile(byte[] s) {        
        this.iccprofile = s;
    }
        
    public void setManufacturerDeviceName(String s) {        
        this.manufacturerdevicename = s;
    }
    
    public void setSizePerPixelXinMM(BigDecimal ppsx) {
        this.ppsx = ppsx;
    }
    
    public void setSizePerPixelYinMM(BigDecimal ppsy) {
        this.ppsy = ppsy;
    }
    
    public byte[] getXMP() {
        Model m = ModelFactory.createDefaultModel();
        Resource root = m.createResource(uuid);
        if (magnification!=null) {
            DecimalFormat f = new DecimalFormat("#.##############################");
            f.setDecimalSeparatorAlwaysShown(false);
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ObjectiveLensPower"), f.format(magnification));
        }
        if (manufacturer!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/Manufacturer"), manufacturer);
        }
        if (manufacturerdevicename!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ManufacturerModelName"), manufacturerdevicename);
        }
        if (iccprofile!=null) {
            Literal lit = m.createTypedLiteral(encodeBase64(iccprofile), XSDDatatype.XSDbase64Binary);
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ICCProfile"), lit);
        }
        if (ImageComments!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ImageComments"), ImageComments);
        }
        if (exposuretime!=null) {
            DecimalFormat f = new DecimalFormat("#.##############################");
            f.setDecimalSeparatorAlwaysShown(false);
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ExposureTime"), m.createLiteral(f.format(exposuretime)));
        }        
        if ((ppsx!=null)&&(ppsy!=null)) {
            ArrayList<RDFNode> list = new ArrayList<>(); 
            DecimalFormat f = new DecimalFormat("#.##############################");
            f.setDecimalSeparatorAlwaysShown(false);      
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/PixelSpacing"), m.createList(m.createLiteral(f.format(ppsy)), m.createLiteral(f.format(ppsx))));
        }
        m.setNsPrefix("DICOM", "http://ns.adobe.com/DICOM/");
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("xmpMM", "http://ns.adobe.com/xap/1.0/mm/");
        m.setNsPrefix("xmp", "http://ns.adobe.com/xap/1.0/");
        m.setNsPrefix("xsd", XSD.NS);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        RDFWriterBuilder builder = RDFWriterBuilder.create();
         builder
            .source(m)
            .lang(Lang.RDFXML)
            .base(uuid)
            .output(os);
        builder.build();
        return os.toByteArray();
    }
    
    public String getXMPString() {
        String packet = new String(getXMP(),StandardCharsets.UTF_8);
        packet = "<?xpacket begin='﻿\uFEFF' id='W5M0MpCehiHzreSzNTczkc9d'?>\n<x:xmpmeta xmlns:x='adobe:ns:meta/' x:xmptk='"+HalcyonSettings.HALCYONSOFTWARE+"'>\n"+packet;
        packet = packet+"</x:xmpmeta>\n"+(new String(new char[2424]).replace('\0', ' '))+"\n<?xpacket end='w'?>";
        return packet;
    }
    
    /**
     * A {@link DocumentBuilderFactory} that will not resolve external entities.
     *
     * <p>XMP packets arrive inside uploaded images, so this XML is attacker-controlled. Parsed by a
     * stock factory, a packet carrying {@code <!DOCTYPE r [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>}
     * has that file read and inlined into the {@code //rdf:RDF} subtree, which {@link #getXMP} then
     * asserts as triples on the resource — handing the uploader any file the server process can read,
     * and giving SSRF to internal hosts through an {@code http:} entity. Reproduced end to end against
     * this class before the fix.
     *
     * <p>Jena already ships exactly this hardening for its own parsers (external general entities,
     * external parameter entities and external DTD loading all off), so reusing it keeps the two in
     * step rather than restating the feature names here and letting them drift.
     */
    private static DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = JenaXMLInput.newDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        // Defence in depth, and the reason it is safe: an XMP packet is
        // <?xpacket?><x:xmpmeta>...</x:xmpmeta>, which never needs a DTD, so refusing the doctype
        // outright costs nothing and stops entity expansion before it starts.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory;
    }

    /**
     * A {@link Transformer} that will not fetch an external DTD or stylesheet while re-serialising
     * the extracted {@code rdf:RDF} node. The DOM it is handed came from an untrusted packet, so the
     * same reasoning as {@link #secureDocumentBuilderFactory()} applies on the way back out.
     */
    private static Transformer secureTransformer() throws TransformerConfigurationException {
        TransformerFactory tf = TransformerFactory.newInstance();
        // Not every implementation knows these attributes; refusing to transform would be worse than
        // running on a JDK whose default factory already forbids both.
        try {
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException e) {
            logger.debug("TransformerFactory does not support external-access limits", e);
        }
        return tf.newTransformer();
    }

    public static Model getXMP(String base, String xml) {
        InputStream xmlis = new ByteArrayInputStream(xml.getBytes());
        Model xmp = ModelFactory.createDefaultModel();
        try {
            DocumentBuilder builder = secureDocumentBuilderFactory().newDocumentBuilder();
            Document doc = builder.parse(xmlis);
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix.equals("rdf")) {
                        return RDF.getURI();
                    }
                    return null;
                }
                @Override
                public Iterator getPrefixes(String val) { return null; }
                @Override
                public String getPrefix(String uri) { return null; }
            });            
            String expression = "//rdf:RDF"; // XPath expression to find the rdf:RDF element
            XPathExpression expr = xpath.compile(expression);
            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            if (node != null) {
                //System.out.println("Found node: " + node.getNodeName());
                StringWriter writer = new StringWriter();
                Transformer transformer = secureTransformer();
                transformer.transform(new DOMSource(node), new StreamResult(writer));                
                //System.out.println(writer.toString());
                byte[] byteArray = writer.toString().getBytes(StandardCharsets.UTF_8);
                RDFParserBuilder.create()
                    .base(base)
                    .source(new ByteArrayInputStream(byteArray))
                    .lang(Lang.RDFXML)
                    .parse(xmp);                   
            }
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        }
        return xmp;
    }
    
    public static InputStream grab() {
        try {
            DocumentBuilder builder = secureDocumentBuilderFactory().newDocumentBuilder();
            Document doc = builder.parse("xmp.xml");
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix.equals("rdf")) {
                        return RDF.getURI();
                    }
                    return null;
                }
                @Override
                public Iterator getPrefixes(String val) { return null; }
                @Override
                public String getPrefix(String uri) { return null; }
            });            
            String expression = "//rdf:RDF"; // XPath expression to find the rdf:RDF element
            XPathExpression expr = xpath.compile(expression);
            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            if (node != null) {
                logger.debug("Found node: {}", node.getNodeName());
                StringWriter writer = new StringWriter();
                Transformer transformer = secureTransformer();
                transformer.transform(new DOMSource(node), new StreamResult(writer));                
                logger.debug("{}", writer.toString());
                byte[] byteArray = writer.toString().getBytes(StandardCharsets.UTF_8);
                return new ByteArrayInputStream(byteArray);                
            } else {
                logger.debug("Node not found.");
                return new FileInputStream("xmp.xml");
            }
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        }
        return null;
    }

    public static void main(String args[]) throws FileNotFoundException {                 
        logger.debug("YAY !!!!=========================================================================================");
        XMP xmp = new XMP();
        xmp.setMagnification(BigDecimal.valueOf(40.4));
        xmp.setSizePerPixelXinMM(BigDecimal.valueOf(0.2468d).divide(BigDecimal.valueOf(1000000)));
        xmp.setSizePerPixelYinMM(BigDecimal.valueOf(0.2468d).divide(BigDecimal.valueOf(1000000)));
        xmp.setExposureTime(BigDecimal.valueOf(0.0041234d));
        logger.debug("{}", xmp.getXMPString());
    }
}
