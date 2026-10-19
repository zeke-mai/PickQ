package com.pickq;
import java.io.*; import java.util.*; import java.util.zip.*; import java.nio.charset.StandardCharsets;
public class ImportTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static List<Map<String,String>> fixture(String xml)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));z.write(xml.getBytes(StandardCharsets.UTF_8));z.closeEntry();}return Xlsx.read(new ByteArrayInputStream(out.toByteArray()));}
 public static void main(String[] args)throws Exception {
  List<Map<String,String>> rows; if(args.length>0){rows=Xlsx.read(new FileInputStream(args[0]));
  check(rows.size()==14,"14 template examples");check(rows.get(0).get("正确答案").equals("A"),"Answer header");check(rows.get(0).get("选项A").equals("酸"),"Option header");check(rows.stream().map(x->x.get("题型")).distinct().count()==9,"Nine types");}
  rows=fixture("<worksheet><sheetData><row><c r='A1' t='inlineStr'><is><t>说明文字</t></is></c></row><row><c r='B2' t='inlineStr'><is><t>答案</t></is></c><c r='D2' t='inlineStr'><is><t>题目</t></is></c></row><row><c r='B3' t='inlineStr'><is><t>A</t></is></c><c r='D3' t='inlineStr'><is><t>测试题</t></is></c></row><row><c r='D4'/></row></sheetData></worksheet>");
  check(rows.size()==1,"Blank rows ignored");check(rows.get(0).get("题干").equals("测试题"),"Reordered sparse inline cells");check(rows.get(0).get("正确答案").equals("A"),"Optional columns absent");
  try{fixture("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///private'>]><worksheet/>");throw new AssertionError("DTD accepted");}catch(IOException expected){}
  System.out.println("PASS: 14 template questions, 9 types, sparse/reordered/missing columns, inline strings, DTD rejection");
 }
}
