package com.softdev.system.generator.util;


import com.softdev.system.generator.entity.ClassInfo;
import com.softdev.system.generator.entity.FieldInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author xuxueli 2018-05-02 21:10:45
 * @modify zhengk/moshow 20180913
 */
public class TableParseUtil {

/*
changDecimal 替换掉decimal(8,2) 中的,
 */

  public static String changComma(String strDecimal,String strComma) {
    byte[] byFieldListTmp = strDecimal.getBytes();
    byte[] byDecimal = strComma.getBytes();//"decimal(".getBytes();
    //byte[] byDouble = "double(".getBytes();
    int fieldListTmpLen = strDecimal.length() - 9;

    //替换掉decimal(8,2) 中的,
    //bool begFlag = false;
    int inx = 0;
    for (int i = 0; i < fieldListTmpLen; ) {
      if (byFieldListTmp[i] != ' ' && byFieldListTmp[i] != '\t') {
        i++;
        continue;
      }

      i++;
      for (inx = 0; inx < byDecimal.length; inx++, i++) {
        if (byFieldListTmp[i] != byDecimal[inx])
          break;
      }
      if (inx < byDecimal.length)
        continue;
      int j = 0;
      for (j = 0; j < 5; j++, i++) {
        if (byFieldListTmp[i] == ',') {
          byFieldListTmp[i] = '#';
          break;
        }
      }
      i++;

    }//For i
    return new String(byFieldListTmp);
  }


  /**
   * 解析建表SQL生成代码（model-dao-xml）
   *
   * @param tableSql
   * @return
   */
  public static ClassInfo processTableIntoClassInfo(String tableSql) throws IOException {
    if (tableSql == null || tableSql.trim().length() == 0) {
      throw new CodeGenerateException("Table structure can not be empty.");
    }
    tableSql = tableSql.trim().replaceAll("'", "`").replaceAll("\"", "`").replaceAll("，", ",").toLowerCase();

    // table Name
    String tableName = null;
    if (tableSql.contains("TABLE") && tableSql.contains("(")) {
      tableName = tableSql.substring(tableSql.indexOf("TABLE") + 5, tableSql.indexOf("("));
    } else if (tableSql.contains("table") && tableSql.contains("(")) {
      tableName = tableSql.substring(tableSql.indexOf("table") + 5, tableSql.indexOf("("));
    } else {
      throw new CodeGenerateException("Table structure anomaly.");
    }

    //新增处理create table if not exists members情况
    if (tableName.contains("if not exists")) tableName = tableName.replaceAll("if not exists", "");

    if (tableName.contains("`")) {
      tableName = tableName.substring(tableName.indexOf("`") + 1, tableName.lastIndexOf("`"));
    } else {
      //空格开头的，需要替换掉\n\t空格
      tableName = tableName.replaceAll(" ", "").replaceAll("\n", "").replaceAll("\t", "");
    }
    //优化对byeas`.`ct_bd_customerdiscount这种命名的支持
    if (tableName.contains("`.`")) {
      tableName = tableName.substring(tableName.indexOf("`.`") + 3);
    } else if (tableName.contains(".")) {
      //优化对likeu.members这种命名的支持
      tableName = tableName.substring(tableName.indexOf(".") + 1);
    }
    // class Name
    String className = StringUtils.upperCaseFirst(StringUtils.underlineToCamelCase(tableName));
    if (className.contains("_")) {
      className = className.replaceAll("_", "");
    }

    // class Comment
    String classComment = null;
    //mysql是comment=,pgsql/oracle是comment on table,
    if (tableSql.contains("comment=")) {
      String classCommentTmp = tableSql.substring(tableSql.lastIndexOf("comment=") + 8).replaceAll("`", "").trim();
      if (classCommentTmp.indexOf(" ") != classCommentTmp.lastIndexOf(" ")) {
        classCommentTmp = classCommentTmp.substring(classCommentTmp.indexOf(" ") + 1, classCommentTmp.lastIndexOf(" "));
      }
      if (classCommentTmp != null && classCommentTmp.trim().length() > 0) {
        classComment = classCommentTmp;
      } else {
        //修复表备注为空问题
        classComment = className;
      }
    } else if (tableSql.contains("comment on table")) {
      //COMMENT ON TABLE CT_BAS_FEETYPE IS 'CT_BAS_FEETYPE';
      String classCommentTmp = tableSql.substring(tableSql.lastIndexOf("comment on table") + 17).trim();
      //证明这是一个常规的COMMENT ON TABLE  xxx IS 'xxxx'
      if (classCommentTmp.contains("`")) {
        classCommentTmp = classCommentTmp.substring(classCommentTmp.indexOf("`") + 1);
        classCommentTmp = classCommentTmp.substring(0, classCommentTmp.indexOf("`"));
        classComment = classCommentTmp;
      } else {
        //非常规的没法分析
        classComment = tableName;
      }
    } else {
      //修复表备注为空问题
      classComment = tableName;
    }
    //如果备注跟;混在一起，需要替换掉
    classComment = classComment.replaceAll(";", "");
    // field List
    List<FieldInfo> fieldList = new ArrayList<FieldInfo>();

    // 正常( ) 内的一定是字段相关的定义。
    String fieldListTmp = tableSql.substring(tableSql.indexOf("(") + 1, tableSql.lastIndexOf(")"));

    // 匹配 comment，替换备注里的小逗号, 防止不小心被当成切割符号切割
    Matcher matcher = Pattern.compile("\\ comment `(.*?)\\`").matcher(fieldListTmp);     // "\\{(.*?)\\}"
    while (matcher.find()) {

      String commentTmp = matcher.group();
      //2018-9-27 zhengk 不替换，只处理，支持COMMENT评论里面多种注释
      //commentTmp = commentTmp.replaceAll("\\ comment `|\\`", " ");      // "\\{|\\}"

      if (commentTmp.contains(",")) {
        String commentTmpFinal = commentTmp.replaceAll(",", "，");
        fieldListTmp = fieldListTmp.replace(matcher.group(), commentTmpFinal);
      }
    }

    //处理decimal问题
    fieldListTmp = changComma(fieldListTmp,"decimal(");
    fieldListTmp = changComma(fieldListTmp,"double(");

    String[] fieldLineList = fieldListTmp.split(",");
    if (fieldLineList.length > 0) {
      int i = 0;//i为了解决primary key关键字出现的地方，出现在前3行，一般和id有关
      for (String columnLine : fieldLineList) {
        i++;
        columnLine = columnLine.replaceAll("\n", "").replaceAll("\t", "").trim();
        // `userid` int(11) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
        // 2018-9-18 zhengk 修改为contains，提升匹配率和匹配不按照规矩出牌的语句
        if (!columnLine.contains("constraint") && !columnLine.contains("using") && !columnLine.contains("unique")
            && !columnLine.contains("storage") && !columnLine.contains("pctincrease")
            && !columnLine.contains("buffer_pool") && !columnLine.contains("tablespace")
            && !(columnLine.contains("primary") && i > 3)) {

          //如果是oracle的number(x,x)，可能出现最后分割残留的,x)，这里做排除处理
          if (columnLine.length() < 5) continue;
          //2018-9-16 zhengkai 支持'符号以及空格的oracle语句// userid` int(11) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
          String columnName = "";
          columnLine = columnLine.replaceAll("`", " ").replaceAll("\"", " ").replaceAll("'", "").replaceAll("  ", " ").trim();
          //如果遇到username varchar(65) default '' not null,这种情况，判断第一个空格是否比第一个引号前
          columnName = columnLine.substring(0, columnLine.indexOf(" "));

          // field Name
          String fieldName = StringUtils.lowerCaseFirst(StringUtils.underlineToCamelCase(columnName));
          if (fieldName.contains("_")) {
            fieldName = fieldName.replaceAll("_", "");
          }

          // field class
          columnLine = columnLine.substring(columnLine.indexOf("`") + 1).trim();  // int(11) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
          String fieldClass = Object.class.getSimpleName();
          //2018-9-16 zhengk 补充char/clob/blob/json等类型，如果类型未知，默认为String
          if (columnLine.contains("int") || columnLine.contains("tinyint") || columnLine.contains("smallint")) {
            fieldClass = Integer.TYPE.getSimpleName();
          } else if (columnLine.contains("bigint")) {
            fieldClass = Long.TYPE.getSimpleName();
          } else if (columnLine.contains("float")) {
            fieldClass = Float.TYPE.getSimpleName();
          } else if (columnLine.contains("double")) {
            fieldClass = Double.TYPE.getSimpleName();
          } else if (columnLine.contains("datetime") || columnLine.contains("timestamp")) {
            fieldClass = Date.class.getSimpleName();
          } else if (columnLine.contains("varchar") || columnLine.contains("text") || columnLine.contains("char")
              || columnLine.contains("clob") || columnLine.contains("blob") || columnLine.contains("json")) {
            fieldClass = String.class.getSimpleName();
          } else if (columnLine.contains("decimal") || columnLine.contains("number")) {
            fieldClass = BigDecimal.class.getSimpleName();
          } else {
            fieldClass = String.class.getSimpleName();
          }

          // field comment，MySQL的一般位于field行，而pgsql和oralce多位于后面。
          String fieldComment = null;
          if (columnLine.contains("comment")) {
            String commentTmp = columnLine.substring(columnLine.indexOf("comment") + 7).trim();  // '用户ID',
            if (commentTmp.contains("`") || commentTmp.indexOf("`") != commentTmp.lastIndexOf("`")) {
              commentTmp = commentTmp.substring(commentTmp.indexOf("`") + 1, commentTmp.lastIndexOf("`"));
            }
            //解决最后一句是评论，无主键且连着)的问题:album_id int(3) default '1' null comment '相册id：0 代表头像 1代表照片墙')
            if (commentTmp.contains(")")) {
              commentTmp = commentTmp.substring(0, commentTmp.lastIndexOf(")"));
            }
            fieldComment = commentTmp;
          } else if (tableSql.contains("comment on column") && tableSql.contains("." + columnName + " is `")) {
            //新增对pgsql/oracle的字段备注支持
            //COMMENT ON COLUMN public.check_info.check_name IS '检查者名称';
            Matcher columnCommentMatcher = Pattern.compile("." + columnName + " is `").matcher(tableSql);     // "\\{(.*?)\\}"
            while (columnCommentMatcher.find()) {
              String columnCommentTmp = columnCommentMatcher.group();
              System.out.println(columnCommentTmp);
              fieldComment = tableSql.substring(tableSql.indexOf(columnCommentTmp) + columnCommentTmp.length()).trim();
              fieldComment = fieldComment.substring(0, fieldComment.indexOf("`")).trim();
            }
          } else {
            //修复comment不存在导致报错的问题
            fieldComment = columnName;
          }

          FieldInfo fieldInfo = new FieldInfo();
          fieldInfo.setColumnName(columnName);
          fieldInfo.setFieldName(fieldName);
          fieldInfo.setFieldClass(fieldClass);
          fieldInfo.setFieldComment(fieldComment);

          fieldList.add(fieldInfo);
        }
      }
    }

    if (fieldList.size() < 1) {
      throw new CodeGenerateException("表结构分析失败，请检查语句或者提交issue给我");
    }

    ClassInfo codeJavaInfo = new ClassInfo();
    codeJavaInfo.setTableName(tableName);
    codeJavaInfo.setClassName(className);
    codeJavaInfo.setClassComment(classComment);
    codeJavaInfo.setFieldList(fieldList);

    return codeJavaInfo;
  }

}
