/*
 * Copyright 2012 Lukas Landis
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
package ch.luklanis.esscan.paymentslip;

import android.util.Log;

public class EsrValidation extends PsValidation {
	private static final String TAG = "ESR Validation";
	private static final int STEP_COUNT = 3; 
    
    private static final char[] CONTROL_CHARS_IN_STEP = {
    	'>', '+', '>'
    };
    
    private static final int[][] VALID_LENGTHS_IN_STEP = {
    	{ 4, 14 },
    	{ 28, 17 },  // TODO check length
    	{ 10, -1 }  // TODO check length
    };
    
    private static final String[] STEP_FORMAT = {
    	"%s", "%s", " %s"
    };
    
    private String[] completeCode;
    
    public EsrValidation() {
		completeCode = new String[STEP_COUNT];
	}
	
	@Override
	public int getStepCount(){
		return STEP_COUNT;
	}

	@Override
	public boolean validate(String text) {
		try{
			// Log.d(TAG, String.format("text: %s", text));
			String related = getRelatedText(text);	
			// Log.d(TAG, String.format("related: %s", related));

			if(related.charAt(related.length() - 1) != CONTROL_CHARS_IN_STEP[currentStep] 
					|| (related.length() != VALID_LENGTHS_IN_STEP[currentStep][0] 
							&& related.length() != VALID_LENGTHS_IN_STEP[currentStep][1])){
				return false;
			}

			int[] digits = getDigitsFromText(related, related.length() - 1);
			int[] withoutCheckDigit = new int[digits.length - 1];

			for(int i = 0; i < withoutCheckDigit.length; i++){
				withoutCheckDigit[i] = digits[i];
			}

			int checkDigit = getCheckDigit(withoutCheckDigit);

			if(checkDigit == digits[digits.length - 1] && 
					additionalStepTest(related)) {
				completeCode[currentStep] = String.format(STEP_FORMAT[currentStep], related);
				return true;
			}
		}
		catch(Exception exc)
		{
			Log.e(TAG, exc.toString());
			return false;
		}
		
		return false;
	}

	private boolean additionalStepTest(String related) {
		if (currentStep == 0) {
			int esrType = Integer.parseInt(related.substring(0, 2));

			switch (esrType) {
			case 4:
			case 14:
			case 31:
			case 33:
				return related.length() == 4;
			default:
				return related.length() > 4;
			}
		}
		
		return true;
	}

	@Override
	public String getRelatedText() {
		return relatedText;
	}

	@Override
	public String getRelatedText(String text) {		
		if(text == null || text == ""){
			return null;
		}
		
		relatedText = text.replaceAll("\\s", "");
		
		if(currentStep > 0){
			int indexOfControlCharBefore = relatedText.indexOf(String.valueOf(CONTROL_CHARS_IN_STEP[currentStep-1]));
			
			if(indexOfControlCharBefore != -1 && indexOfControlCharBefore < (relatedText.length() - 1)){
				relatedText = relatedText.substring(indexOfControlCharBefore + 1);
			}
		}
		
		int indexOfCurrentControlChar = relatedText.indexOf(String.valueOf(CONTROL_CHARS_IN_STEP[currentStep]));
		
		if(indexOfCurrentControlChar != -1 && indexOfCurrentControlChar != (relatedText.length() - 1)){
			relatedText = relatedText.substring(0, indexOfCurrentControlChar + 1);
		}
		
		return relatedText;
	}

	@Override
	public String getCompleteCode() {
		String result = "";
		
		for(int i = 0; i < completeCode.length; i++){
			if(completeCode[i] != null){
				result += completeCode[i];
			}
		}
		
		return result;
	}

	@Override
	public void resetCompleteCode() {
		if(completeCode == null){
			return;
		}
		
		for(int i = 0; i < completeCode.length; i++){
			completeCode[i] = null;
		}
	}

	@Override
	public String getSpokenType() {
		return EsrResult.PS_TYPE_NAME;
	}
}
