/**
 * 表格单元格文字样式转 CSS 样式对象
 * @param style 单元格样式数据
 */
export const getTextStyle = (style = {}) => {
  const styleObj = {};
  if ("fontWeight" in style) styleObj.fontWeight = style.fontWeight;
  else if (style.bold === true) styleObj.fontWeight = "bold";
  if ("fontStyle" in style) styleObj.fontStyle = style.fontStyle;
  if ("textDecoration" in style) styleObj.textDecoration = style.textDecoration;
  if ("color" in style) styleObj.color = style.color;
  if ("bgColor" in style) styleObj.backgroundColor = style.bgColor;
  else if ("backcolor" in style) styleObj.backgroundColor = style.backcolor;
  if (style.fontsize) styleObj.fontSize = style.fontsize + "px";
  styleObj.lineHeight = 1.5;
  if (style.sort) styleObj.sorted = style.sort;
  return styleObj;
};

/**
 * 格式化单元格文字：换行转为 <br>，空格转为 &nbsp;
 * @param text 原始文字
 */
export const formatText = (text) => {
  if (!text) return "";
  let result = String(text).replace(/\n/g, "</br>");
  result = result.replace(/ /g, "&nbsp;");
  return result;
};
