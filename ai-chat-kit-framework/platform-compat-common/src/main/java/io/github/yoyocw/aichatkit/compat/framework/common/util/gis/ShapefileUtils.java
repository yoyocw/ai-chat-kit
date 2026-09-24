package io.github.yoyocw.aichatkit.compat.framework.common.util.gis;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shapefile 空间数据转换工具。
 *
 * <p>适用于将同一数据集的 {@code .shp/.shx/.dbf/.prj/.cpg} 组成文件转换为一个 GeoJSON 文件。</p>
 */
public final class ShapefileUtils {

    /**
     * 完整 Shapefile 数据集必须包含的文件扩展名。
     */
    private static final List<String> REQUIRED_EXTENSIONS =
            Arrays.asList(".shp", ".shx", ".dbf", ".prj");

    /**
     * GeoJSON 坐标小数精度；避免 GeoTools 默认四位小数造成米级边界形变。
     */
    private static final int GEOJSON_DECIMAL_PRECISION = 15;

    private ShapefileUtils() {
    }

    /**
     * 将 Shapefile 数据集转换为 GeoJSON 文件。
     *
     * @param shapefileFiles 同一数据集的 Shapefile 组成文件，必须包含同名 .shp/.shx/.dbf/.prj 文件，.cpg 可选
     * @param geoJsonPath    GeoJSON 输出文件路径，已有文件将被覆盖
     * @param sourceCoordinateSystem 源坐标系标识，例如 EPSG:4524；为空时使用 .prj 中的坐标系
     * @param targetCoordinateSystem 目标坐标系标识，例如 EPSG:4326；为空时不执行重投影
     * @param simplifyTolerance 拓扑简化容差，单位为源坐标系单位；0 表示不简化
     * @throws IOException Shapefile 读取或 GeoJSON 写入失败
     */
    public static void convertToGeoJson(List<Path> shapefileFiles, Path geoJsonPath,
                                        String sourceCoordinateSystem,
                                        String targetCoordinateSystem,
                                        double simplifyTolerance) throws IOException {
        Path shapefilePath = validateAndFindShapefile(shapefileFiles);
        // 编码说明文件可缺省；仅匹配当前数据集，避免使用其他文件的编码。
        Path cpgPath = shapefileFiles.stream()
                .filter(path -> baseName(path).equalsIgnoreCase(baseName(shapefilePath)))
                .filter(path -> extension(path).equalsIgnoreCase(".cpg"))
                .findFirst().orElse(null);
        Charset charset = readCharset(cpgPath);

        ShapefileDataStore dataStore = new ShapefileDataStore(shapefilePath.toUri().toURL());
        try {
            dataStore.setCharset(charset);
            SimpleFeatureSource featureSource = dataStore.getFeatureSource();
            SimpleFeatureCollection features = featureSource.getFeatures();
            CoordinateReferenceSystem declaredSourceCrs = featureSource.getSchema().getCoordinateReferenceSystem();
            CoordinateReferenceSystem sourceCrs = resolveCoordinateSystem(
                    declaredSourceCrs, sourceCoordinateSystem, "源");
            CoordinateReferenceSystem targetCrs = resolveCoordinateSystem(
                    sourceCrs, targetCoordinateSystem, "目标");
            MathTransform transform = createTransform(sourceCrs, targetCrs);
            double effectiveTolerance = sourceCrs instanceof ProjectedCRS ? simplifyTolerance : 0D;
            writeFeatureCollection(features, transform, targetCrs, effectiveTolerance, geoJsonPath);
        } finally {
            dataStore.dispose();
        }
    }

    /**
     * 解析接口指定的坐标系；参数为空时使用默认坐标系。
     */
    private static CoordinateReferenceSystem resolveCoordinateSystem(CoordinateReferenceSystem defaultCrs,
                                                                     String coordinateSystem,
                                                                     String coordinateSystemType) {
        if (coordinateSystem == null || coordinateSystem.trim().isEmpty()) {
            if (defaultCrs == null) {
                throw new IllegalArgumentException("Shapefile .prj 文件无法识别源坐标系");
            }
            return defaultCrs;
        }
        try {
            return CRS.decode(coordinateSystem.trim(), true);
        } catch (FactoryException ex) {
            throw new IllegalArgumentException("不支持的" + coordinateSystemType + "坐标系：" + coordinateSystem, ex);
        }
    }

    /**
     * 创建源坐标系到目标坐标系的真实重投影变换。
     */
    private static MathTransform createTransform(CoordinateReferenceSystem sourceCrs,
                                                 CoordinateReferenceSystem targetCrs) {
        try {
            return CRS.findMathTransform(sourceCrs, targetCrs, true);
        } catch (FactoryException ex) {
            throw new IllegalArgumentException("源坐标系无法转换到目标坐标系", ex);
        }
    }

    /**
     * 先按源坐标单位简化边界，再执行坐标变换并流式写入 GeoJSON。
     */
    private static void writeFeatureCollection(SimpleFeatureCollection features,
                                               MathTransform transform,
                                               CoordinateReferenceSystem outputCrs,
                                               double simplifyTolerance,
                                               Path geoJsonPath) throws IOException {
        FeatureJSON featureJson = new FeatureJSON(new GeometryJSON(GEOJSON_DECIMAL_PRECISION));
        try (Writer writer = Files.newBufferedWriter(geoJsonPath, StandardCharsets.UTF_8);
            SimpleFeatureIterator iterator = features.features()) {
            writer.write("{\"type\":\"FeatureCollection\",\"crs\":");
            writer.write(featureJson.toString(outputCrs));
            writer.write(",\"features\":[");
            boolean first = true;
            while (iterator.hasNext()) {
                if (!first) {
                    writer.write(',');
                }
                writer.write(featureJson.toString(transformFeature(
                        iterator.next(), transform, simplifyTolerance)));
                first = false;
            }
            writer.write("]}");
        }
    }

    /**
     * 对单个要素执行拓扑保持简化和坐标变换，不修改数据源返回的原始要素。
     */
    private static SimpleFeature transformFeature(SimpleFeature sourceFeature,
                                                  MathTransform transform,
                                                  double simplifyTolerance) throws IOException {
        SimpleFeature outputFeature = SimpleFeatureBuilder.copy(sourceFeature);
        Object geometryValue = sourceFeature.getDefaultGeometry();
        if (!(geometryValue instanceof Geometry)) {
            return outputFeature;
        }
        Geometry geometry = (Geometry) geometryValue;
        Geometry simplified = simplifyTolerance > 0
                ? TopologyPreservingSimplifier.simplify(geometry, simplifyTolerance) : geometry;
        try {
            outputFeature.setDefaultGeometry(transform.isIdentity() ? simplified : JTS.transform(simplified, transform));
            return outputFeature;
        } catch (TransformException ex) {
            throw new IOException("Shapefile 几何坐标转换失败", ex);
        }
    }

    /**
     * 校验组成文件属于同一数据集，并返回唯一的 Shapefile 主文件。
     */
    private static Path validateAndFindShapefile(List<Path> shapefileFiles) {
        if (shapefileFiles == null || shapefileFiles.isEmpty()) {
            throw new IllegalArgumentException("Shapefile 组成文件不能为空");
        }
        if (shapefileFiles.stream().anyMatch(path -> path == null || !Files.isRegularFile(path))) {
            throw new IllegalArgumentException("Shapefile 组成文件不存在");
        }
        List<Path> mainFiles = shapefileFiles.stream()
                .filter(path -> extension(path).equalsIgnoreCase(".shp"))
                .collect(Collectors.toList());
        if (mainFiles.size() != 1) {
            throw new IllegalArgumentException("Shapefile 数据集必须且只能包含一个 .shp 文件");
        }
        Path shapefilePath = mainFiles.get(0);
        String baseName = baseName(shapefilePath);
        for (String extension : REQUIRED_EXTENSIONS) {
            findComponent(shapefileFiles, baseName, extension);
        }
        return shapefilePath;
    }

    /**
     * 查找同名组成文件，确保几何、索引、属性、投影和编码属于同一数据集。
     */
    private static Path findComponent(List<Path> shapefileFiles, String baseName, String extension) {
        return shapefileFiles.stream()
                .filter(path -> baseName(path).equalsIgnoreCase(baseName))
                .filter(path -> extension(path).equalsIgnoreCase(extension))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Shapefile 数据集缺少同名 " + extension + " 文件"));
    }

    /**
     * 从 CPG 文件读取 DBF 属性编码，避免中文属性乱码。
     */
    private static Charset readCharset(Path cpgPath) throws IOException {
        // ZIP 未提供编码文件时默认 UTF-8；存在但声明无效时仍拒绝，避免静默误解码。
        if (cpgPath == null) {
            return StandardCharsets.UTF_8;
        }
        String charsetName = new String(Files.readAllBytes(cpgPath), StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim();
        if ("65001".equals(charsetName)) {
            charsetName = StandardCharsets.UTF_8.name();
        } else if ("936".equals(charsetName)) {
            charsetName = "GBK";
        }
        try {
            return Charset.forName(charsetName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Shapefile .cpg 字符集不受支持：" + charsetName, ex);
        }
    }

    private static String baseName(Path path) {
        String fileName = path.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
    }

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex < 0 ? "" : fileName.substring(extensionIndex);
    }
}
