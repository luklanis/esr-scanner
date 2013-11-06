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

import ch.luklanis.esscan.R;
import ch.luklanis.esscan.history.BankProfile;

public class IbanTest extends TestCase {

    public static void testIbanValidation() {
        // Invalid
        assertEquals("IBAN should be invalid",
                BankProfile.validateIBAN("CH93 0076 2011 6238 5295 6"),
                R.string.msg_own_iban_is_not_valid);

        // Valid, but not from Switzerland
        assertEquals("IBAN should be invalid because it's not from switzerland",
                BankProfile.validateIBAN("DE99 2032 0500 4989 1234 56"),
                R.string.msg_own_iban_is_not_valid);

        // Valid
        assertEquals("IBAN should be valid",
                BankProfile.validateIBAN("CH93 0076 2011 6238 5295 7"),
                0);
    }
}
