package com.pickq;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Reads values only. Template prose and formulas are never executed. */
public final class Xlsx {
 public static List<Map<String,String>> read(InputStream input) throws Exception {
  Map<String,byte[]> entries=new HashMap<>(); long total=0;
  try(ZipInputStream zip=new ZipInputStream(input)) { ZipEntry e; byte[] buf=new byte[8192];
   while((e=zip.getNextEntry())!=null) { if(!e.getName().endsWith(".xml"))continue;
    ByteArrayOutputStream out=new ByteArrayOutputStream(); int n;
    while((n=zip.read(buf))!=-1){ total+=n; if(total>32*1024*1024)throw new IOException("文件解压后超过 32MB");out.write(buf,0,n); }
    entries.put(e.getName(),out.toByteArray());
   }
  }
  List<String> strings=new ArrayList<>();
  if(entries.containsKey("xl/sharedStrings.xml")){NodeList ns=xml(entries.get("xl/sharedStrings.xml")).getElementsByTagName("si");for(int i=0;i<ns.getLength();i++)strings.add(text((Element)ns.item(i),"t"));}
  List<Map<String,String>> result=new ArrayList<>();
  List<String> sheets=new ArrayList<>(); for(String s:entries.keySet())if(s.matches("xl/worksheets/sheet[0-9]+.xml"))sheets.add(s); Collections.sort(sheets);
  for(String sheet:sheets){Map<Integer,String> headers=null;NodeList rows=xml(entries.get(sheet)).getElementsByTagName("row");
   for(int i=0;i<rows.getLength();i++){Map<Integer,String> cells=new TreeMap<>();NodeList cs=((Element)rows.item(i)).getElementsByTagName("c");
    for(int j=0;j<cs.getLength();j++){Element c=(Element)cs.item(j);String ref=c.getAttribute("r");int col=0;for(char ch:ref.toCharArray()){if(ch<'A'||ch>'Z')break;col=col*26+ch-'A'+1;}String v=text(c,"v");if(c.getAttribute("t").equals("s"))v=strings.get(Integer.parseInt(v));else if(c.getAttribute("t").equals("inlineStr"))v=text(c,"t");cells.put(col,v.trim());}
    if(headers==null){Map<Integer,String> candidate=new HashMap<>();for(Map.Entry<Integer,String> e:cells.entrySet())candidate.put(e.getKey(),header(e.getValue()));if(candidate.containsValue("题干"))headers=candidate;continue;}
    Map<String,String> row=new HashMap<>();for(Map.Entry<Integer,String> e:cells.entrySet())if(headers.containsKey(e.getKey()))row.put(headers.get(e.getKey()),e.getValue());if(!row.getOrDefault("题干","").isEmpty())result.add(row);
   }
  }
  if(result.isEmpty())throw new IOException("没有找到题目，请保留题干列的表头");return result;
 }
 static String header(String s){s=s.replaceAll("\\s","").replaceAll("[（(].*","");if(s.equals("题目"))return "题干";if(s.equals("答案"))return "正确答案";return s;}
 static String text(Element e,String tag){NodeList n=e.getElementsByTagName(tag);StringBuilder s=new StringBuilder();for(int i=0;i<n.getLength();i++)s.append(n.item(i).getTextContent());return s.toString();}
 static Document xml(byte[] b)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();String probe=new String(b,java.nio.charset.StandardCharsets.ISO_8859_1).replace("\u0000", "");if(probe.toUpperCase(Locale.ROOT).contains("<!DOCTYPE"))throw new IOException("不支持包含 DTD 的 XML");f.setExpandEntityReferences(false);return f.newDocumentBuilder().parse(new ByteArrayInputStream(b));}
}
