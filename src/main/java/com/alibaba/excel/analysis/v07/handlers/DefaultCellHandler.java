package com.alibaba.excel.analysis.v07.handlers;

import static com.alibaba.excel.constant.ExcelXmlConstants.CELL_FORMULA_TAG;
import static com.alibaba.excel.constant.ExcelXmlConstants.CELL_INLINE_STRING_VALUE_TAG;
import static com.alibaba.excel.constant.ExcelXmlConstants.CELL_TAG;
import static com.alibaba.excel.constant.ExcelXmlConstants.CELL_VALUE_TAG;
import static com.alibaba.excel.constant.ExcelXmlConstants.CELL_VALUE_TYPE_TAG;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.xml.sax.Attributes;

import com.alibaba.excel.analysis.v07.XlsxCellHandler;
import com.alibaba.excel.analysis.v07.XlsxRowResultHolder;
import com.alibaba.excel.constant.ExcelXmlConstants;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.CellData;
import com.alibaba.excel.read.metadata.holder.ReadRowHolder;
import com.alibaba.excel.util.BooleanUtils;
import com.alibaba.excel.util.PositionUtils;
import com.alibaba.excel.util.StringUtils;

/**
 * Cell Handler
 *
 * @author jipengfei
 */
public class DefaultCellHandler implements XlsxCellHandler, XlsxRowResultHolder {
    private final AnalysisContext analysisContext;
    private String currentTag;
    private String currentCellIndex;
    private int curRow;
    private int curCol;
    private List<CellData> curRowContent = new ArrayList<CellData>();
    private CellData currentCellData;

    public DefaultCellHandler(AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Override
    public void clearResult() {
        curRowContent.clear();
    }

    @Override
    public boolean support(String name) {
        return CELL_VALUE_TAG.equals(name) || CELL_FORMULA_TAG.equals(name) || CELL_INLINE_STRING_VALUE_TAG.equals(name)
            || CELL_TAG.equals(name);
    }

    @Override
    public void startHandle(String name, Attributes attributes) {
        currentTag = name;
        // start a cell
        if (CELL_TAG.equals(name)) {
            currentCellIndex = attributes.getValue(ExcelXmlConstants.POSITION);
            int nextRow = PositionUtils.getRow(currentCellIndex);
            if (nextRow > curRow) {
                curRow = nextRow;
            }
            analysisContext
                .readRowHolder(new ReadRowHolder(curRow, analysisContext.readSheetHolder().getGlobalConfiguration()));
            curCol = PositionUtils.getCol(currentCellIndex);

            // t="s" ,it's means String
            // t="inlineStr" ,it's means String
            // t="b" ,it's means Boolean
            // t="e" ,it's means Error
            // t="n" ,it's means Number
            // t is null ,it's means Empty or Number
            CellDataTypeEnum type = CellDataTypeEnum.buildFromCellType(attributes.getValue(CELL_VALUE_TYPE_TAG));
            currentCellData = new CellData(type);
        }
        // cell is formula
        if (CELL_FORMULA_TAG.equals(name)) {
            currentCellData.setFormula(Boolean.TRUE);
        }
    }

    @Override
    public void endHandle(String name) {
        if (CELL_VALUE_TAG.equals(name)) {
            // Have to go "sharedStrings.xml" and get it
            if (currentCellData.getType() == CellDataTypeEnum.STRING) {
                String stringValue = analysisContext.readWorkbookHolder().getReadCache()
                    .get(Integer.valueOf(currentCellData.getStringValue()));
                if (stringValue != null && analysisContext.currentReadHolder().globalConfiguration().getAutoTrim()) {
                    stringValue = stringValue.trim();
                }
                currentCellData.setStringValue(stringValue);
            }
            curRowContent.set(curCol, currentCellData);
        }
        // This is a special form of string
        if (CELL_INLINE_STRING_VALUE_TAG.equals(name)) {
            XSSFRichTextString richTextString = new XSSFRichTextString(currentCellData.getStringValue());
            String stringValue = richTextString.toString();
            if (stringValue != null && analysisContext.currentReadHolder().globalConfiguration().getAutoTrim()) {
                stringValue = stringValue.trim();
            }
            currentCellData.setStringValue(stringValue);
            curRowContent.set(curCol, currentCellData);
        }
    }

    @Override
    public void appendCurrentCellValue(String currentCellValue) {
        if (StringUtils.isEmpty(currentCellValue)) {
            return;
        }
        if (currentTag == null) {
            return;
        }
        if (CELL_FORMULA_TAG.equals(currentTag)) {
            currentCellData.setFormulaValue(currentCellValue);
            return;
        }
        CellDataTypeEnum oldType = currentCellData.getType();
        switch (oldType) {
            case STRING:
            case ERROR:
                currentCellData.setStringValue(currentCellValue);
                break;
            case BOOLEAN:
                currentCellData.setBooleanValue(BooleanUtils.valueOf(currentCellValue));
                break;
            case NUMBER:
            case EMPTY:
                currentCellData.setType(CellDataTypeEnum.NUMBER);
                currentCellData.setDoubleValue(Double.valueOf(currentCellValue));
                break;
            default:
                throw new IllegalStateException("Cannot set values now");
        }
    }

    @Override
    public List<CellData> getCurRowContent() {
        return this.curRowContent;
    }

    @Override
    public int getColumnSize() {
        return this.curCol;
    }

}
