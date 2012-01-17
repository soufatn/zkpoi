/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.ss.formula.functions;

import org.apache.poi.hssf.HSSFTestDataSamples;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.OperationEvaluationContext;
import org.apache.poi.ss.formula.eval.AreaEval;
import org.apache.poi.ss.formula.eval.NumberEval;
import org.apache.poi.ss.formula.eval.ValueEval;

import junit.framework.TestCase;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.SheetBuilder;

import java.util.Date;

/**
 * Tests for {@link Subtotal}
 *
 * @author Paul Tomlin
 */
public final class TestSubtotal extends TestCase {
	private static final int FUNCTION_AVERAGE = 1;
	private static final int FUNCTION_COUNT = 2;
	private static final int FUNCTION_MAX = 4;
	private static final int FUNCTION_MIN = 5;
	private static final int FUNCTION_PRODUCT = 6;
	private static final int FUNCTION_STDEV = 7;
	private static final int FUNCTION_SUM = 9;

	private static final double[] TEST_VALUES0 = {
		1, 2,
		3, 4,
		5, 6,
		7, 8,
		9, 10
	};

	private static void confirmSubtotal(int function, double expected) {
		ValueEval[] values = new ValueEval[TEST_VALUES0.length];
		for (int i = 0; i < TEST_VALUES0.length; i++) {
			values[i] = new NumberEval(TEST_VALUES0[i]);
		}

		AreaEval arg1 = EvalFactory.createAreaEval("C1:D5", values);
		ValueEval args[] = { new NumberEval(function), arg1 };

		ValueEval result = new Subtotal().evaluate(args, 0, 0);

		assertEquals(NumberEval.class, result.getClass());
		assertEquals(expected, ((NumberEval) result).getNumberValue(), 0.0);
	}

	public void testBasics() {
		confirmSubtotal(FUNCTION_SUM, 55.0);
		confirmSubtotal(FUNCTION_AVERAGE, 5.5);
		confirmSubtotal(FUNCTION_COUNT, 10.0);
		confirmSubtotal(FUNCTION_MAX, 10.0);
		confirmSubtotal(FUNCTION_MIN, 1.0);
		confirmSubtotal(FUNCTION_PRODUCT, 3628800.0);
		confirmSubtotal(FUNCTION_STDEV, 3.0276503540974917);
	}

     public void testAvg(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(1,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue(1);
        Cell a5 = sh.createRow(4).createCell(0);
        a5.setCellValue(7);
        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(1,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(1,A1:A6)");

        fe.evaluateAll();

        assertEquals(2.0, a3.getNumericCellValue());
        assertEquals(8.0, a6.getNumericCellValue());
        assertEquals(3.0, a7.getNumericCellValue());
    }

    public void testSum(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(9,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue(1);
        Cell a5 = sh.createRow(4).createCell(0);
        a5.setCellValue(7);
        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(9,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(9,A1:A6)");

        fe.evaluateAll();

        assertEquals(4.0, a3.getNumericCellValue());
        assertEquals(26.0, a6.getNumericCellValue());
        assertEquals(12.0, a7.getNumericCellValue());
    }

    public void testCount(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(2,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue("POI");                  // A4 is string and not counted
        Cell a5 = sh.createRow(4).createCell(0); // A5 is blank and not counted

        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(2,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(2,A1:A6)");

        fe.evaluateAll();

        assertEquals(2.0, a3.getNumericCellValue());
        assertEquals(6.0, a6.getNumericCellValue());
        assertEquals(2.0, a7.getNumericCellValue());
    }

    public void testCounta(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(3,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue("POI");                  // A4 is string and not counted
        Cell a5 = sh.createRow(4).createCell(0); // A5 is blank and not counted

        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(3,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(3,A1:A6)");

        fe.evaluateAll();

        assertEquals(2.0, a3.getNumericCellValue());
        assertEquals(8.0, a6.getNumericCellValue());
        assertEquals(3.0, a7.getNumericCellValue());
    }

    public void testMax(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(4,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue(1);
        Cell a5 = sh.createRow(4).createCell(0);
        a5.setCellValue(7);
        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(4,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(4,A1:A6)");

        fe.evaluateAll();

        assertEquals(3.0, a3.getNumericCellValue());
        assertEquals(16.0, a6.getNumericCellValue());
        assertEquals(7.0, a7.getNumericCellValue());
    }

    public void testMin(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(5,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue(1);
        Cell a5 = sh.createRow(4).createCell(0);
        a5.setCellValue(7);
        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(5,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(5,A1:A6)");

        fe.evaluateAll();

        assertEquals(1.0, a3.getNumericCellValue());
        assertEquals(4.0, a6.getNumericCellValue());
        assertEquals(1.0, a7.getNumericCellValue());
    }

    public void testStdev(){

        Workbook wb = new HSSFWorkbook();

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();

        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellValue(3);
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(7,A1:A2)");
        Cell a4 = sh.createRow(3).createCell(0);
        a4.setCellValue(1);
        Cell a5 = sh.createRow(4).createCell(0);
        a5.setCellValue(7);
        Cell a6 = sh.createRow(5).createCell(0);
        a6.setCellFormula("SUBTOTAL(7,A1:A5)*2 + 2");
        Cell a7 = sh.createRow(6).createCell(0);
        a7.setCellFormula("SUBTOTAL(7,A1:A6)");

        fe.evaluateAll();

        assertEquals(1.41421, a3.getNumericCellValue(), 0.0001);
        assertEquals(7.65685, a6.getNumericCellValue(), 0.0001);
        assertEquals(2.82842, a7.getNumericCellValue(), 0.0001);
    }

    public void test50209(){
        Workbook wb = new HSSFWorkbook();
        Sheet sh = wb.createSheet();
        Cell a1 = sh.createRow(0).createCell(0);
        a1.setCellValue(1);
        Cell a2 = sh.createRow(1).createCell(0);
        a2.setCellFormula("SUBTOTAL(9,A1)");
        Cell a3 = sh.createRow(2).createCell(0);
        a3.setCellFormula("SUBTOTAL(9,A1:A2)");

        FormulaEvaluator fe = wb.getCreationHelper().createFormulaEvaluator();
        fe.evaluateAll();
        assertEquals(1.0, a2.getNumericCellValue());
        assertEquals(1.0, a3.getNumericCellValue());
    }

    private static void confirmExpectedResult(FormulaEvaluator evaluator, String msg, Cell cell, double expected) {

        CellValue value = evaluator.evaluate(cell);
        if (value.getErrorValue() != 0)
            throw new RuntimeException(msg + ": " + value.formatAsString());
        assertEquals(msg, expected, value.getNumberValue());
    }

    public void testFunctionsFromTestSpreadsheet() {
        HSSFWorkbook workbook = HSSFTestDataSamples.openSampleWorkbook("SubtotalsNested.xls");
        HSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        assertEquals("A1", 10.0, sheet.getRow(0).getCell(0).getNumericCellValue());
        assertEquals("A2", 20.0, sheet.getRow(1).getCell(0).getNumericCellValue());

        //Test simple subtotal over one area
        Cell cellA3 = sheet.getRow(2).getCell(0);
        confirmExpectedResult(evaluator, "A3", cellA3, 30.0);

        //Test existence of the second area
        assertNotNull("B1 must not be null", sheet.getRow(0).getCell(1));
        assertEquals("B1", 7.0, sheet.getRow(0).getCell(1).getNumericCellValue());

        Cell cellC1 = sheet.getRow(0).getCell(2);
        Cell cellC2 = sheet.getRow(1).getCell(2);
        Cell cellC3 = sheet.getRow(2).getCell(2);

        //Test Functions SUM, COUNT and COUNTA calculation of SUBTOTAL
        //a) areas A and B are used
        //b) first 2 subtotals don't consider the value of nested subtotal in A3
        confirmExpectedResult(evaluator, "SUBTOTAL(SUM;A1:A7;B1:B7)", cellC1, 37.0);
        confirmExpectedResult(evaluator, "SUBTOTAL(COUNT;A1:A7;B1:B7)", cellC2, 3.0);
        confirmExpectedResult(evaluator, "SUBTOTAL(COUNTA;A1:A7;B1:B7)", cellC3, 5.0);
    }
}