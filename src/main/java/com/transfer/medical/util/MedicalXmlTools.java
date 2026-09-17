package com.transfer.medical.util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * XML 解析工具。
 *
 * @author yuanjihong
 */
public class MedicalXmlTools {

    private static final Logger log = LoggerFactory.getLogger(MedicalXmlTools.class);

    private MedicalXmlTools() {
    }

    /**
     * 将 XML 根节点下的直接子节点解析为 name -&gt; text 映射。
     *
     * @param xmlPath XML 全路径
     * @return 子节点映射
     */
    public static Map<String, Object> xml2map(String xmlPath) throws DocumentException {
        Document document = new SAXReader().read(new File(xmlPath));
        Element root = document.getRootElement();
        List<Element> children = root.elements();
        return children.stream().collect(Collectors.toMap(Element::getName, Element::getText));
    }
}
