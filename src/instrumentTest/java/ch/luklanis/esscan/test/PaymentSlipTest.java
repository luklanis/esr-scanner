package ch.luklanis.esscan.test;/*
 * Copyright 2013 Lukas Landis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import junit.framework.TestCase;

import ch.luklanis.esscan.paymentslip.EsIbanValidation;
import ch.luklanis.esscan.paymentslip.EsResult;
import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.EsrValidation;
import ch.luklanis.esscan.paymentslip.PsResult;

public class PaymentSlipTest extends TestCase {
    public static void testEsIbanValidation() {
        EsIbanValidation esIbanValidation = new EsIbanValidation();

        // invalid first part
        boolean result = esIbanValidation.validate("000");
        assertEquals("1 First part of code row should be invalid", false, result);

        // invalid second part
        esIbanValidation.gotoBeginning(true);
        result = esIbanValidation.validate("000000000000001234567890128+ 000");
        assertEquals("2 First part of code row should be valid", true, result);

        assertEquals("2 Should have a further step", esIbanValidation.nextStep(), true);
        result = esIbanValidation.validate("000000000000001234567890128+ 000");
        assertEquals("2 Second part of code row should be invalid", false, result);

        // invalid third part
        esIbanValidation.gotoBeginning(true);
        result = esIbanValidation.validate("000000000000001234567890128+ 070888854>\n000");
        assertEquals("3 First part of code row should be valid", true, result);

        assertEquals("3 Should have a further step", esIbanValidation.nextStep(), true);
        result = esIbanValidation.validate("000000000000001234567890128+ 070888854>\n000");
        assertEquals("3 Second part of code row should be valid", true, result);
        esIbanValidation.gotoBeginning(false);  // run this instead of nextStep because it's a new line

        result = esIbanValidation.validate("000");
        assertEquals("3 Third part of code row should be invalid", false, result);

        // valid
        esIbanValidation.gotoBeginning(true);
        result = esIbanValidation.validate("000000000000001234567890128+ 070888854>\n000");
        assertEquals("4 First part of code row should be valid", true, result);
        assertEquals("4 Should have a further step", esIbanValidation.nextStep(), true);

        result = esIbanValidation.validate("000000000000001234567890128+ 070888854>\n000");
        assertEquals("4 Second part of code row should be valid", true, result);
        esIbanValidation.gotoBeginning(false);  // run this instead of nextStep because it's a new line

        result = esIbanValidation.validate("800009393>");
        assertEquals("4 Third part of code row should be valid", true, result);
        assertEquals("4 Should not have a further step", esIbanValidation.nextStep(), false);

        assertEquals("5 Should be finished", esIbanValidation.finished(), true);
    }

    public static void testEsrValidation() {
        EsrValidation esrValidation = new EsrValidation();

        // invalid first part
        boolean result = esrValidation.validate("000");
        assertEquals("1 First part of code row should be invalid", false, result);

        // invalid second part
        esrValidation.gotoBeginning(true);
        result = esrValidation.validate("1100003949754>000");
        assertEquals("2 First part of code row should be valid", true, result);
        assertEquals("2 Should have a further step", esrValidation.nextStep(), true);

        result = esrValidation.validate("1100003949754>000");
        assertEquals("2 Second part of code row should be invalid", false, result);

        // invalid third part
        esrValidation.gotoBeginning(true);
        result = esrValidation.validate("1100003949754>210000000003139471430009017+ 000");
        assertEquals("3 First part of code row should be valid", true, result);
        assertEquals("3 Should have a further step", esrValidation.nextStep(), true);

        result = esrValidation.validate("1100003949754>210000000003139471430009017+ 000");
        assertEquals("3 Second part of code row should be valid", true, result);
        assertEquals("3 Should have a further step", esrValidation.nextStep(), true);

        result = esrValidation.validate("1100003949754>210000000003139471430009017+ 000");
        assertEquals("3 Third part of code row should be invalid", false, result);

        // valid
        esrValidation.gotoBeginning(true);
        for (int i = 0; i < 3; i++) {
            result = esrValidation.validate("1100003949754>210000000003139471430009017+ 010001628>");
            assertEquals(String.format("4.%d Should be valid", i), true, result);
            assertEquals(String.format("4.%d Should have a further step", i),
                    esrValidation.nextStep(),
                    i == 2 ? false : true);
        }

        assertEquals("5 Should be finished", esrValidation.finished(), true);
    }

    public static void testPsResult() {
        PsResult esrResult = PsResult.getInstance(
                "1100003949754>210000000003139471430009017+ 010001628>");
        assertTrue("Should be an instance of EsrResult", esrResult instanceof EsrResult);
        assertEquals("Account", "01-162-8", esrResult.getAccount());
        assertEquals("Currency", "3949.75", ((EsrResult) esrResult).getAmount());
        assertEquals("Reference",
                "21 00000 00003 13947 14300 09017",
                ((EsrResult) esrResult).getReference());

        PsResult esResult = PsResult.getInstance(
                "000000000000001234567890128+ 070888854>\n800009393>");
        assertTrue("Should be an instance of EsResult", esResult instanceof EsResult);
        assertEquals("Account", "80-939-3", esResult.getAccount());
        assertEquals("Reference",
                "000000000000001234567890128",
                ((EsResult) esResult).getReference());
        assertEquals("Clearing", "070888854", ((EsResult) esResult).getClearing());
    }
}
