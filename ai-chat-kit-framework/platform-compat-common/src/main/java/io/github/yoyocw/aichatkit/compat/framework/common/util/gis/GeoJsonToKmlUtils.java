package io.github.yoyocw.aichatkit.compat.framework.common.util.gis;

import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * GeoJSON 转 KML 工具类。
 *
 * <p>输入坐标按 GeoJSON 规范视为经度、纬度和可选高程，输出 KML 2.2 文档。
 * 支持 FeatureCollection、Feature 以及所有标准 GeoJSON 几何类型。</p>
 */
public final class GeoJsonToKmlUtils {

    private static final String KML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>";

    private static final String KML_FOOTER = "</Document></kml>";

    private GeoJsonToKmlUtils() {
    }

    /**
     * 将 UTF-8 编码的 GeoJSON 文件内容转换为 KML 文件内容。
     *
     * @param geoJsonBytes GeoJSON 文件字节，必须是合法的 UTF-8 JSON
     * @return UTF-8 编码的 KML 文件字节
     * @throws IllegalArgumentException GeoJSON 结构、几何类型或坐标不合法时抛出
     */
    public static byte[] convert(byte[] geoJsonBytes) {
        if (geoJsonBytes == null || geoJsonBytes.length == 0) {
            throw new IllegalArgumentException("GeoJSON 文件内容不能为空");
        }
        JsonNode root;
        try {
            root = JsonUtils.parseTree(geoJsonBytes);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("GeoJSON 文件内容不是合法 JSON", ex);
        }
        StringBuilder kml = new StringBuilder(Math.max(1024, geoJsonBytes.length));
        kml.append(KML_HEADER);
        appendRoot(root, kml);
        kml.append(KML_FOOTER);
        return kml.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 根据 GeoJSON 根对象类型写入要素或几何。
     */
    private static void appendRoot(JsonNode root, StringBuilder kml) {
        validateObject(root, "GeoJSON 根节点");
        String type = requiredText(root, "type", "GeoJSON 根节点缺少 type");
        if ("FeatureCollection".equals(type)) {
            JsonNode features = root.get("features");
            if (features == null || !features.isArray()) {
                throw new IllegalArgumentException("FeatureCollection.features 必须是数组");
            }
            for (JsonNode feature : features) {
                appendFeature(feature, kml);
            }
        } else if ("Feature".equals(type)) {
            appendFeature(root, kml);
        } else {
            appendGeometryPlacemark(root, kml);
        }
    }

    /**
     * 将单个 GeoJSON Feature 写为 KML Placemark，并保留标量属性到 ExtendedData。
     */
    private static void appendFeature(JsonNode feature, StringBuilder kml) {
        validateObject(feature, "Feature");
        if (!"Feature".equals(requiredText(feature, "type", "Feature 缺少 type"))) {
            throw new IllegalArgumentException("features 数组只能包含 Feature 对象");
        }
        JsonNode properties = feature.get("properties");
        JsonNode geometry = feature.get("geometry");
        kml.append("<Placemark>");
        appendFeatureName(feature, properties, kml);
        appendExtendedData(properties, kml);
        if (geometry != null && !geometry.isNull()) {
            appendGeometry(geometry, kml);
        }
        kml.append("</Placemark>");
    }

    /**
     * 将裸 GeoJSON 几何写为无属性的 KML Placemark。
     */
    private static void appendGeometryPlacemark(JsonNode geometry, StringBuilder kml) {
        kml.append("<Placemark>");
        appendGeometry(geometry, kml);
        kml.append("</Placemark>");
    }

    /**
     * 优先使用 properties.name，其次使用 Feature.id 作为 KML 要素名称。
     */
    private static void appendFeatureName(JsonNode feature, JsonNode properties, StringBuilder kml) {
        JsonNode name = properties != null && properties.isObject() ? properties.get("name") : null;
        if (name == null || name.isNull() || name.asText().trim().isEmpty()) {
            name = feature.get("id");
        }
        if (name != null && !name.isNull()) {
            kml.append("<name>").append(escapeXml(name.asText())).append("</name>");
        }
    }

    /**
     * 将 Feature 属性写入 KML ExtendedData，复杂属性按原 JSON 文本保留。
     */
    private static void appendExtendedData(JsonNode properties, StringBuilder kml) {
        if (properties == null || properties.isNull()) {
            return;
        }
        if (!properties.isObject()) {
            throw new IllegalArgumentException("Feature.properties 必须是对象或 null");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        if (!fields.hasNext()) {
            return;
        }
        kml.append("<ExtendedData>");
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            kml.append("<Data name=\"").append(escapeXml(field.getKey())).append("\"><value>")
                    .append(escapeXml(propertyValue(field.getValue())))
                    .append("</value></Data>");
        }
        kml.append("</ExtendedData>");
    }

    private static String propertyValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isContainerNode() ? value.toString() : value.asText();
    }

    /**
     * 按标准 GeoJSON 几何类型分派 KML 几何写入逻辑。
     */
    private static void appendGeometry(JsonNode geometry, StringBuilder kml) {
        validateObject(geometry, "geometry");
        String type = requiredText(geometry, "type", "geometry 缺少 type");
        if ("GeometryCollection".equals(type)) {
            appendGeometryCollection(geometry.get("geometries"), kml);
            return;
        }
        JsonNode coordinates = geometry.get("coordinates");
        if ("Point".equals(type)) {
            appendPoint(coordinates, kml);
        } else if ("LineString".equals(type)) {
            appendLineString(coordinates, kml);
        } else if ("Polygon".equals(type)) {
            appendPolygon(coordinates, kml);
        } else if ("MultiPoint".equals(type)) {
            appendMultiPoint(coordinates, kml);
        } else if ("MultiLineString".equals(type)) {
            appendMultiLineString(coordinates, kml);
        } else if ("MultiPolygon".equals(type)) {
            appendMultiPolygon(coordinates, kml);
        } else {
            throw new IllegalArgumentException("不支持的 GeoJSON 几何类型：" + type);
        }
    }

    private static void appendPoint(JsonNode coordinate, StringBuilder kml) {
        kml.append("<Point><coordinates>");
        appendCoordinate(coordinate, kml);
        kml.append("</coordinates></Point>");
    }

    private static void appendLineString(JsonNode coordinates, StringBuilder kml) {
        validateCoordinateArray(coordinates, "LineString.coordinates");
        kml.append("<LineString><coordinates>");
        appendCoordinateSequence(coordinates, kml);
        kml.append("</coordinates></LineString>");
    }

    private static void appendPolygon(JsonNode coordinates, StringBuilder kml) {
        validateCoordinateArray(coordinates, "Polygon.coordinates");
        if (coordinates.size() == 0) {
            throw new IllegalArgumentException("Polygon 至少需要一个线性环");
        }
        kml.append("<Polygon>");
        appendBoundary("outerBoundaryIs", coordinates.get(0), kml);
        for (int i = 1; i < coordinates.size(); i++) {
            appendBoundary("innerBoundaryIs", coordinates.get(i), kml);
        }
        kml.append("</Polygon>");
    }

    private static void appendBoundary(String boundaryType, JsonNode ring, StringBuilder kml) {
        validateCoordinateArray(ring, "Polygon 线性环");
        kml.append('<').append(boundaryType).append("><LinearRing><coordinates>");
        appendCoordinateSequence(ring, kml);
        kml.append("</coordinates></LinearRing></").append(boundaryType).append('>');
    }

    private static void appendMultiPoint(JsonNode coordinates, StringBuilder kml) {
        validateCoordinateArray(coordinates, "MultiPoint.coordinates");
        kml.append("<MultiGeometry>");
        for (JsonNode coordinate : coordinates) {
            appendPoint(coordinate, kml);
        }
        kml.append("</MultiGeometry>");
    }

    private static void appendMultiLineString(JsonNode coordinates, StringBuilder kml) {
        validateCoordinateArray(coordinates, "MultiLineString.coordinates");
        kml.append("<MultiGeometry>");
        for (JsonNode line : coordinates) {
            appendLineString(line, kml);
        }
        kml.append("</MultiGeometry>");
    }

    private static void appendMultiPolygon(JsonNode coordinates, StringBuilder kml) {
        validateCoordinateArray(coordinates, "MultiPolygon.coordinates");
        kml.append("<MultiGeometry>");
        for (JsonNode polygon : coordinates) {
            appendPolygon(polygon, kml);
        }
        kml.append("</MultiGeometry>");
    }

    private static void appendGeometryCollection(JsonNode geometries, StringBuilder kml) {
        if (geometries == null || !geometries.isArray()) {
            throw new IllegalArgumentException("GeometryCollection.geometries 必须是数组");
        }
        kml.append("<MultiGeometry>");
        for (JsonNode geometry : geometries) {
            appendGeometry(geometry, kml);
        }
        kml.append("</MultiGeometry>");
    }

    private static void appendCoordinateSequence(JsonNode coordinates, StringBuilder kml) {
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) {
                kml.append(' ');
            }
            appendCoordinate(coordinates.get(i), kml);
        }
    }

    /**
     * 按 KML 的经度,纬度,高程顺序写入单个坐标，忽略 GeoJSON 中第四维及后续维度。
     */
    private static void appendCoordinate(JsonNode coordinate, StringBuilder kml) {
        if (coordinate == null || !coordinate.isArray() || coordinate.size() < 2
                || !coordinate.get(0).isNumber() || !coordinate.get(1).isNumber()) {
            throw new IllegalArgumentException("GeoJSON 坐标必须至少包含经度和纬度两个数值");
        }
        kml.append(coordinate.get(0).asText()).append(',').append(coordinate.get(1).asText());
        if (coordinate.size() > 2 && coordinate.get(2).isNumber()) {
            kml.append(',').append(coordinate.get(2).asText());
        }
    }

    private static void validateCoordinateArray(JsonNode coordinates, String fieldName) {
        if (coordinates == null || !coordinates.isArray()) {
            throw new IllegalArgumentException(fieldName + " 必须是数组");
        }
    }

    private static void validateObject(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(fieldName + " 必须是对象");
        }
    }

    private static String requiredText(JsonNode node, String fieldName, String message) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.asText();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
