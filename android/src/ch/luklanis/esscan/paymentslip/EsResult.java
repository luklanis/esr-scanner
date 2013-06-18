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

/**
 * Encapsulates the result of OCR.
 */
public final class EsResult extends PsResult{

	public static final String PS_TYPE_NAME = "red";
	
	private String reason;

	public EsResult(String completeCode) {
		super(completeCode);
		reason = "";
	}
	
	public EsResult(String completeCode, long timestamp) {
		super(completeCode, timestamp);
		reason = "";
	}
	
	public EsResult(String completeCode, String reason, long timestamp) {
		super(completeCode, timestamp);
		this.reason = reason;
	}

	public String getReference(){
		String code = completeCode;
		int indexOfPlus = code.indexOf('+');

		if(indexOfPlus < 0){
			return "?";
		}

		return code.substring(0, indexOfPlus);
	}

	public String getClearing(){
		String code = completeCode;
		int indexOfSpace = code.indexOf(' ');

		if(indexOfSpace < 0){
			return "?";
		}

		return code.substring((indexOfSpace + 1), (indexOfSpace + 10));
	}
	
	public String getReason() {
		return reason;
	}
	
	public void setReason(String reason) {
		this.reason = reason;
	}

	@Override
	public String getAccount(){
		String code = completeCode;
		int indexOfNewLine = code.indexOf('\n');

		if(indexOfNewLine < 0){
			return "?";
		}

		int indentureNumber = Integer.parseInt(code.substring((indexOfNewLine + 3), (indexOfNewLine + 9)));

		return code.substring((indexOfNewLine + 1), (indexOfNewLine + 3)) + "-" + String.valueOf(indentureNumber) + "-"
		+ code.substring(indexOfNewLine + 9, indexOfNewLine + 10);
	}

	@Override
	public String toString() {
		return PS_TYPE_NAME + " payment slip," + (completeCode.indexOf("+") > 0 ? " first" : " second") + " code row";
	}

	@Override
	public int getMaxAddressLength() {
		return 24;
	}
}
