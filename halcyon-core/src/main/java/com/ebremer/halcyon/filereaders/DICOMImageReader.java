package com.ebremer.halcyon.filereaders;

import com.ebremer.dcm2rdf.Dcm2RdfBuilder;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import com.ebremer.ns.PROVO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;
import org.slf4j.LoggerFactory;

/**
 * A {@link ImageReader} for DICOM VL Whole Slide Microscopy pathology images.
 * <p>
 * A single slide is stored across several DICOM SOP Instances (one file per
 * pyramid level, plus optional {@code LABEL} and {@code OVERVIEW} images).
 * Opened on any file of the slide, this reader discovers the sibling files that
 * share its {@code SeriesInstanceUID} and {@code ImageType} flavour and presents
 * them as a single multi-resolution {@link ImageMeta} — mirroring the way
 * {@link TiffImageReader} exposes a pyramidal TIFF's sub-images.
 * <p>
 * All metadata (for pyramid discovery and {@link #getMeta()}) comes from
 * {@code com.ebremer:dcm2rdf}, which converts each DICOM header to a Jena
 * {@link Model}; the structural fields are pulled with SPARQL and {@code getMeta()}
 * is a SPARQL {@code CONSTRUCT} that filters that model down. Pixel tiles (the
 * {@code TILED_FULL} frames, typically JPEG&nbsp;2000) are decoded through
 * dcm4che's ImageIO plugin.
 *
 * @author erich
 */
public class DICOMImageReader extends AbstractImageReader {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DICOMImageReader.class);
    private static final int METAVERSION = 0;
    /** The Halcyon DICOM RDF namespace that dcm2rdf mints (dcm:GGGGEEEE tags, dcm:SOPInstance). */
    private static final String DCM_NS = "https://halcyon.is/dicom/ns/";

    /** dcm2rdf configured to match the Halcyon DICOM RDF model: urn:oid UIDs,
     *  cdt:List multi-values, and source-file provenance. Reusable / thread-safe. */
    private static final Dcm2RdfBuilder D2R = new Dcm2RdfBuilder().oid(true).cdt(true).extra(true);

    /** DimensionOrganizationType (0020,9311) — the frame ordering of a tiled instance. */
    private static final String TILED_FULL = "TILED_FULL";
    private static final String TILED_SPARSE = "TILED_SPARSE";

    // M10: every geometry field below is read straight out of an untrusted file
    // header and then used to size allocations and index frames, so each one is
    // bounded here. The limits are far above anything real: observed slides use
    // 256x256 tiles (label/overview strips 401x6, 1280x16) and total matrices up to
    // ~166,908 x 84,951.
    /** Max per-tile edge. Real: 256..1280. */
    private static final int MAX_TILE_DIM = 16384;
    /** Max pixels in one decoded tile — ~256 MB at 4 B/px, the actual OOM lever. */
    private static final long MAX_TILE_PIXELS = 64L * 1024 * 1024;
    /** Max total-matrix edge. Real: ~166,908. Also keeps {@code width + tileW} clear of int overflow. */
    private static final int MAX_MATRIX_DIM = 1 << 20;

    private final URI uri;
    private final URI base;
    private final ImageMeta meta;
    private final List<Level> levels;
    private final Model selfModel;   // dcm2rdf model of the opened file
    private final File baseFile;     // full-resolution VOLUME base of the discovered pyramid
    private final boolean master;
    private long sizeInBytes;

    public DICOMImageReader(URI uri, URI base) throws IOException {
        logger.info("DICOMImageReader(URI uri, URI base) {} {}", uri, base);
        this.uri = uri;
        this.base = base;
        File self = new File(uri);
        DcmInfo selfInfo = probe(self);
        if (selfInfo == null) {
            throw new IOException("Not a readable DICOM file: " + self);
        }
        this.selfModel = selfInfo.model();
        String series = selfInfo.series();
        String flavor = selfInfo.flavor();

        // Group the slide's files: same filename stem, confirmed by matching
        // Series UID + ImageType flavour. VOLUME -> full pyramid; LABEL/OVERVIEW -> single image.
        List<DcmInfo> group = new ArrayList<>();
        for (File f : siblingCandidates(self)) {
            DcmInfo info;
            if (sameFile(f, self)) {
                info = selfInfo;
            } else {
                try {
                    info = probe(f);
                } catch (IOException ex) {
                    logger.warn("skipping sibling {} : {}", f, ex.getMessage());
                    continue;
                }
            }
            if (info == null) {
                continue;
            }
            if (series != null && !series.equals(info.series())) {
                continue;
            }
            if (!flavor.equals(info.flavor())) {
                continue;
            }
            group.add(info);
        }
        if (group.isEmpty()) {
            group.add(selfInfo);
        }
        // Base first: order by total pixel matrix width, descending.
        group.sort(Comparator.comparingInt(DcmInfo::width).reversed());

        this.levels = new ArrayList<>();
        long total = 0;
        for (int s = 0; s < group.size(); s++) {
            DcmInfo info = group.get(s);
            Level lvl = new Level(s, info.file(), info.width(), info.height(), info.tileW(), info.tileH(), info.numFrames());
            levels.add(lvl);
            total += lvl.fileSize;
        }
        this.sizeInBytes = total;
        DcmInfo baseInfo = group.get(0);
        this.baseFile = baseInfo.file();
        // "Master" = opened on the full-resolution VOLUME base, not a resampled
        // level / label / overview. Only the master advertises itself as a
        // so:ImageObject, so ListImages shows one row per slide.
        this.master = "VOLUME".equals(baseInfo.flavor()) && sameFile(self, baseFile);

        Level b = levels.get(0);
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, b.width, b.height)
            .setTileSizeX(b.tileW)
            .setTileSizeY(b.tileH);
        // Include the base (scale 1) as well as the resampled levels so that
        // getBestMatch() can select full resolution — each level is its own file.
        for (Level lvl : levels) {
            builder.addScale(lvl.series, lvl.width, lvl.height);
        }
        this.meta = builder.build();
    }

    @Override
    public int getMetaVersion() {
        return METAVERSION;
    }

    @Override
    public String getFormat() {
        return "dcm";
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("dcm");
        set.add("dicom");
        return set;
    }

    @Override
    public ImageMeta getImageMeta() {
        return meta;
    }

    private BufferedImage readTile(ImageRegion r, int series) {
        try {
            return levels.get(series).readRegion(r);
        } catch (IOException ex) {
            logger.error("readTile failed series={} region={} : {}", series, r, ex.getMessage());
            return null;
        }
    }

    @Override
    public BufferedImage readTile(ImageRegion region, com.ebremer.halcyon.lib.Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth() / (double) preferredsize.width(), (double) region.getHeight() / (double) preferredsize.height()));
        return ImageTools.ScaleBufferedImage(readTile(scale.Validate(region.scaleRegion(scale.scale())), scale.series()), preferredsize, true);
    }

    @Override
    public Model readTileMeta(ImageRegion region, com.ebremer.halcyon.lib.Rectangle preferredsize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Model getMeta() {
        return getMeta(uri);
    }

    @Override
    public Model getMeta(URI xuri) {
        // CONSTRUCT the curated DICOM subset from the dcm2rdf model, remapping the
        // urn:oid SOP-instance subject onto the Halcyon image URI.
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            CONSTRUCT { ?img ?p ?o }
            WHERE {
                ?s a dcm:SOPInstance ; ?p ?o .
                VALUES ?p {
                    dcm:00080060 dcm:00080016 dcm:00080018 dcm:0020000D dcm:0020000E
                    dcm:00080008 dcm:00209311 dcm:00280008 dcm:00280010 dcm:00280011
                    dcm:00480006 dcm:00480007 prov:wasDerivedFrom
                }
                FILTER (!isBlank(?o))
            }
            """
        );
        pss.setNsPrefix("dcm", DCM_NS);
        pss.setNsPrefix("prov", PROVO.NS);
        pss.setIri("img", URITools.fix(base));
        Model m;
        try (QueryExecution qe = QueryExecutionFactory.create(pss.asQuery(), selfModel)) {
            m = qe.execConstruct();
        }
        Resource root = m.getResource(URITools.fix(base));
        Resource bnode = m.createResource()
            .addProperty(LWS.mediaType, "application/dicom")
            .addLiteral(LWS.sizeInBytes, sizeInBytes);
        root.addProperty(LWS.representation, bnode)
            .addLiteral(HAL.filemetaversion, m.createTypedLiteral(METAVERSION, XSD.integer.getURI()))
            .addProperty(RDF.type, LWS.DataResource)
            .addProperty(RDF.type, m.createResource(DCM_NS + "SOPInstance"));
        // Only the master (full-res VOLUME base) is typed so:ImageObject and carries
        // the matrix dimensions, so ListImages lists one row per slide rather than
        // one per pyramid level / label / overview.
        if (master) {
            root.addProperty(RDF.type, SchemaDO.ImageObject)
                .addLiteral(EXIF.width, m.createTypedLiteral(meta.getWidth(), XSD.integer.getURI()))
                .addLiteral(EXIF.height, m.createTypedLiteral(meta.getHeight(), XSD.integer.getURI()));
        }
        m.setNsPrefix("dcm", DCM_NS);
        m.setNsPrefix("sdo", SchemaDO.NS);
        m.setNsPrefix("hal", HAL.NS);
        m.setNsPrefix("lws", LWS.NS);
        m.setNsPrefix("exif", EXIF.NS);
        m.setNsPrefix("prov", PROVO.NS);
        m.setNsPrefix("xsd", XSD.getURI());
        return m;
    }

    @Override
    public void close() {
        levels.forEach(Level::close);
    }

    // ---- metadata via dcm2rdf ------------------------------------------

    /** Structural fields for one DICOM file, plus its dcm2rdf model. */
    private record DcmInfo(File file, Model model, String series, String flavor,
                           int width, int height, int tileW, int tileH, int numFrames,
                           String organization) {}

    /** Convert a DICOM file to RDF (header only) and pull the fields we need. */
    private static DcmInfo probe(File f) throws IOException {
        Model fm = D2R.toModel(f);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            SELECT ?series ?itype ?w ?h ?cols ?rows ?frames ?org WHERE {
                ?s a dcm:SOPInstance .
                OPTIONAL { ?s dcm:0020000E ?series }
                OPTIONAL { ?s dcm:00080008 ?itype }
                OPTIONAL { ?s dcm:00480006 ?w }
                OPTIONAL { ?s dcm:00480007 ?h }
                OPTIONAL { ?s dcm:00280011 ?cols }
                OPTIONAL { ?s dcm:00280010 ?rows }
                OPTIONAL { ?s dcm:00280008 ?frames }
                OPTIONAL { ?s dcm:00209311 ?org }
            } LIMIT 1
            """
        );
        pss.setNsPrefix("dcm", DCM_NS);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.asQuery(), fm)) {
            ResultSet rs = qe.execSelect();
            if (!rs.hasNext()) {
                return null;
            }
            QuerySolution qs = rs.next();
            String series = qs.contains("series") ? qs.get("series").toString() : null;
            String itype = qs.contains("itype") ? qs.get("itype").asLiteral().getLexicalForm() : "";
            String flavor = itype.contains("VOLUME") ? "VOLUME"
                          : itype.contains("LABEL") ? "LABEL"
                          : itype.contains("OVERVIEW") ? "OVERVIEW" : "VOLUME";
            int cols = intOf(qs, "cols", 256);
            int rows = intOf(qs, "rows", 256);
            int w = intOf(qs, "w", cols);
            int h = intOf(qs, "h", rows);
            int frames = intOf(qs, "frames", 1);
            String org = strOf(qs, "org");
            // M9/M10: reject before any of this reaches an allocation or a frame index.
            validate(f, w, h, cols, rows, frames, org);
            return new DcmInfo(f, fm, series, flavor, w, h, cols, rows, frames, org);
        }
    }

    /**
     * M9/M10: refuse a header this reader cannot honour, rather than rendering from it.
     * <p>
     * Called from {@link #probe}, so the effect is: the opened file itself fails the
     * constructor with this message, while a bad sibling is merely skipped (the group
     * loop already catches IOException per sibling).
     */
    private static void validate(File f, int width, int height, int tileW, int tileH,
                                int frames, String org) throws IOException {
        // M10: tileW/tileH reach `(width + tileW - 1) / tileW` in the Level ctor, so a
        // header declaring Columns=0 was an ArithmeticException (/ by zero) out of the
        // constructor, and a negative one silently produced a bogus tilesPerRow.
        if (tileW <= 0 || tileH <= 0) {
            throw new IOException("DICOM declares a non-positive tile size (Columns=" + tileW
                    + " Rows=" + tileH + "): " + f);
        }
        if (width <= 0 || height <= 0) {
            throw new IOException("DICOM declares a non-positive pixel matrix ("
                    + width + "x" + height + "): " + f);
        }
        if (frames <= 0) {
            throw new IOException("DICOM declares NumberOfFrames=" + frames + ": " + f);
        }
        // M10: the decode-time OOM lever — dr.read(frame) allocates tileW*tileH.
        if (tileW > MAX_TILE_DIM || tileH > MAX_TILE_DIM
                || (long) tileW * (long) tileH > MAX_TILE_PIXELS) {
            throw new IOException("DICOM declares an implausible tile size " + tileW + "x" + tileH
                    + " (max " + MAX_TILE_DIM + " per edge, " + MAX_TILE_PIXELS + " px): " + f);
        }
        if (width > MAX_MATRIX_DIM || height > MAX_MATRIX_DIM) {
            throw new IOException("DICOM declares an implausible pixel matrix " + width + "x" + height
                    + " (max " + MAX_MATRIX_DIM + " per edge): " + f);
        }
        // M9: the frame index below is `row * tilesPerRow + col`, which is ONLY the
        // frame order of TILED_FULL. A TILED_SPARSE instance stores each frame's
        // position in its Per-Frame Functional Groups (Plane Position (Slide)) and may
        // omit tiles entirely, so that formula silently returns the WRONG TISSUE — no
        // exception, just a scrambled slide. For pathology that is worse than an
        // error, so anything this reader cannot order is refused. (The tag is absent
        // on non-WSI DICOM — a plain MR/CT — which stays readable while it has a
        // single frame, since frame 0 is then the only answer the formula can give.)
        if (org.isEmpty()) {
            if (frames > 1) {
                throw new IOException("DICOM has " + frames + " frames but no DimensionOrganizationType"
                        + " (0020,9311); frame order is unknown: " + f);
            }
            return;
        }
        if (!TILED_FULL.equals(org)) {
            throw new IOException("DICOM DimensionOrganizationType=" + org + " is not supported"
                    + (TILED_SPARSE.equals(org)
                        ? " (TILED_SPARSE needs the per-frame Plane Position (Slide) map; no sample"
                          + " exists to verify an implementation against)"
                        : "")
                    + ": " + f);
        }
    }

    /**
     * M10: never let a malformed header throw out of here. {@code asLiteral().getInt()}
     * raises NumberFormatException/DatatypeFormatException on a literal that is not a
     * number (a header claiming Columns="abc"), which used to escape the constructor
     * as an unhandled runtime exception rather than a readable failure.
     */
    private static int intOf(QuerySolution qs, String var, int dflt) {
        if (!qs.contains(var)) {
            return dflt;
        }
        RDFNode n = qs.get(var);
        if (!n.isLiteral()) {
            return dflt;
        }
        try {
            return n.asLiteral().getInt();
        } catch (RuntimeException ex) {
            logger.warn("DICOM header field {} is not an integer ({}) — using default {}",
                    var, n, dflt);
            return dflt;
        }
    }

    /** Plain-literal string field, or "" — dcm2rdf emits (0020,9311) as a bare string. */
    private static String strOf(QuerySolution qs, String var) {
        if (!qs.contains(var)) {
            return "";
        }
        RDFNode n = qs.get(var);
        return n.isLiteral() ? n.asLiteral().getLexicalForm().trim() : "";
    }

    private static boolean sameFile(File a, File b) {
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    /** Files in the same directory whose name shares this file's slide stem (name minus trailing {@code _<n>_<n>}). */
    private static List<File> siblingCandidates(File self) {
        List<File> out = new ArrayList<>();
        String key = groupKey(self.getName());
        File dir = self.getParentFile();
        File[] files = (dir == null) ? null : dir.listFiles();
        if (files == null) {
            out.add(self);
            return out;
        }
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".dcm") && groupKey(f.getName()).equals(key)) {
                out.add(f);
            }
        }
        if (out.isEmpty()) {
            out.add(self);
        }
        return out;
    }

    private static String groupKey(String fileName) {
        String s = fileName;
        int dot = s.toLowerCase().lastIndexOf(".dcm");
        if (dot >= 0) {
            s = s.substring(0, dot);
        }
        return s.replaceAll("_\\d+_\\d+$", "");
    }

    /** One pyramid level = one DICOM SOP Instance / file. */
    private static final class Level {
        final int series;
        final File file;
        final int width;
        final int height;
        final int tileW;
        final int tileH;
        final int tilesPerRow;
        final int numFrames;
        final long fileSize;
        private javax.imageio.ImageReader reader;
        private ImageInputStream iis;

        Level(int series, File file, int width, int height, int tileW, int tileH, int numFrames) {
            // M10: probe()/validate() has already bounded these, but this ctor divides by
            // tileW and is the thing that actually blew up (ArithmeticException on
            // Columns=0). Keep the invariant local so it cannot be reintroduced by a
            // future caller that skips validate().
            if (tileW <= 0 || tileH <= 0) {
                throw new IllegalArgumentException("tile size must be positive, got "
                        + tileW + "x" + tileH + " for " + file);
            }
            this.series = series;
            this.file = file;
            this.width = width;
            this.height = height;
            this.tileW = tileW;
            this.tileH = tileH;
            this.numFrames = numFrames;
            this.tilesPerRow = Math.max(1, (width + tileW - 1) / tileW);
            this.fileSize = file.length();
        }

        private javax.imageio.ImageReader reader() throws IOException {
            if (reader == null) {
                // dcm4che's OpenCV StreamSegment only recognises FileImageInputStream /
                // memory streams; ImageIO.createImageInputStream() would hand back a
                // TwelveMonkeys stream it cannot segment.
                iis = new FileImageInputStream(file);
                reader = new DicomImageReaderSpi().createReaderInstance(null);
                reader.setInput(iis, false, false);
            }
            return reader;
        }

        /** Decode the tiles covering region {@code r} (level pixel coords) and composite them. */
        BufferedImage readRegion(ImageRegion r) throws IOException {
            int x = Math.max(0, r.getX());
            int y = Math.max(0, r.getY());
            int w = Math.min(r.getWidth(), width - x);
            int h = Math.min(r.getHeight(), height - y);
            if (w <= 0 || h <= 0) {
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                javax.imageio.ImageReader dr = reader();
                ImageReadParam p = dr.getDefaultReadParam();
                int cx0 = x / tileW;
                int cx1 = (x + w - 1) / tileW;
                int ry0 = y / tileH;
                int ry1 = (y + h - 1) / tileH;
                for (int ry = ry0; ry <= ry1; ry++) {
                    for (int cx = cx0; cx <= cx1; cx++) {
                        // M9: valid ONLY for TILED_FULL, where frames are laid out in
                        // raster order with no gaps. That is now an enforced invariant,
                        // not an assumption: validate() refuses any instance whose
                        // DimensionOrganizationType is not TILED_FULL (or absent with a
                        // single frame), because for TILED_SPARSE this formula returns
                        // the wrong frame and silently composites the wrong tissue.
                        int frame = ry * tilesPerRow + cx; // 0-based, TILED_FULL
                        if (frame < 0 || frame >= numFrames) {
                            continue;
                        }
                        BufferedImage tile = dr.read(frame, p);
                        g.drawImage(tile, cx * tileW - x, ry * tileH - y, null);
                    }
                }
            } finally {
                g.dispose();
            }
            return out;
        }

        void close() {
            if (reader != null) {
                reader.dispose();
                reader = null;
            }
            if (iis != null) {
                try {
                    iis.close();
                } catch (IOException ex) {
                    logger.debug("closing {} : {}", file, ex.getMessage());
                }
                iis = null;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        File file = new File("D:\\HalcyonStorage\\tcga\\coad\\dicom\\TCGA-CM-6162-01Z-00-DX1.806a99a3-cda2-4dde-8d13-d22912b44d49_0_0.dcm");
        try (DICOMImageReader reader = new DICOMImageReader(file.toURI(), file.toURI())) {
            logger.debug("{}", reader.getImageMeta());
            RDFDataMgr.write(System.out, reader.getMeta(), Lang.TURTLE);
            BufferedImage thumb = reader.readTile(
                new ImageRegion(0, 0, reader.getImageMeta().getWidth(), reader.getImageMeta().getHeight()),
                new com.ebremer.halcyon.lib.Rectangle(1024, 1024));
            File out = new File("D:\\HalcyonStorage\\tcga\\coad\\dicomrdf\\_thumb.png");
            ImageIO.write(thumb, "png", out);
            logger.debug("wrote {} {}x{}", out, thumb.getWidth(), thumb.getHeight());
        }
    }
}
