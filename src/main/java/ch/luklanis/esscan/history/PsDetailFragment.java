package ch.luklanis.esscan.history;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ch.luklanis.esscan.R;
import ch.luklanis.esscan.codesend.GetSendServiceCallback;
import ch.luklanis.esscan.codesend.IEsrSender;
import ch.luklanis.esscan.paymentslip.DTAFileCreator;
import ch.luklanis.esscan.paymentslip.EsResult;
import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.PsResult;

public class PsDetailFragment extends Fragment {

    public static final String ARG_POSITION = "history_position";

    protected static final String TAG = PsDetailFragment.class.getSimpleName();

    public static final int SEND_COMPONENT_CODE_ROW = 0;
    public static final int SEND_COMPONENT_REFERENCE = 1;

    private HistoryManager historyManager;

    private HistoryItem historyItem;

    private final Button.OnClickListener addressChangeListener = new Button.OnClickListener() {
        @Override
        public void onClick(View view) {
            showAddressDialog(view);
        }
    };

    private final Button.OnClickListener resultCopyListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            ClipboardManager clipboardManager = (ClipboardManager) getActivity().getSystemService(
                    Activity.CLIPBOARD_SERVICE);
            clipboardManager.setText(historyItem.getResult().getCompleteCode());

            // clipboardManager.setPrimaryClip(ClipData.newPlainText("ocrResult",
            // ocrResultView.getText()));
            // if (clipboardManager.hasPrimaryClip()) {
            if (clipboardManager.hasText()) {
                Toast toast = Toast.makeText(v.getContext(),
                        R.string.msg_copied,
                        Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.BOTTOM, 0, 0);
                toast.show();
            }
        }
    };

    private final View.OnClickListener exportAgainListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
            builder.setTitle(R.string.msg_sure)
                    .setMessage(R.string.msg_click_ok_to_export_again)
                    .setNeutralButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            historyManager.updateHistoryItemFileName(historyItem.getResult()
                                    .getCompleteCode(), null);

                            TextView dtaFilenameTextView = (TextView) getView().findViewById(R.id.result_dta_file);
                            dtaFilenameTextView.setText("");
                        }
                    })
                    .show();
        }
    };

    private int listPosition;

    public PsDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        historyManager = new HistoryManager(getActivity());

        if (getArguments().containsKey(ARG_POSITION)) {
            listPosition = getArguments().getInt(ARG_POSITION);
            historyItem = historyManager.buildHistoryItem(listPosition);
        } else {
            historyItem = null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_ps_detail, container, false);

        if (historyItem == null) {
            return rootView;
        }

        PsResult psResult = historyItem.getResult();

        ImageView bitmapImageView = (ImageView) rootView.findViewById(R.id.image_view);

        if (psResult instanceof EsrResult) {
            bitmapImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ez_or));
        } else {
            bitmapImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ez_red));
        }

        TextView accountTextView = (TextView) rootView.findViewById(R.id.result_account);
        accountTextView.setText(psResult.getAccount());

        TextView reasonTextView = (TextView) rootView.findViewById(R.id.result_reason_text);
        EditText reasonEditText = (EditText) rootView.findViewById(R.id.result_reason);

        EditText amountEditText = (EditText) rootView.findViewById(R.id.result_amount_edit);
        TextView amountTextView = (TextView) rootView.findViewById(R.id.result_amount);

        if (psResult instanceof EsrResult) {
            EsrResult result = (EsrResult) psResult;

            String amountFromCode = result.getAmount();
            if (amountFromCode != "") {
                amountEditText.setVisibility(View.GONE);
                amountTextView.setVisibility(View.VISIBLE);
                amountTextView.setText(amountFromCode);
            } else {
                amountTextView.setVisibility(View.GONE);
                amountEditText.setVisibility(View.VISIBLE);
                String amountManuel = historyItem.getAmount();
                if (amountManuel == null || amountManuel == "" || amountManuel.length() == 0) {
                    amountEditText.setText(R.string.result_amount_not_set);
                    amountEditText.selectAll();
                } else {
                    amountEditText.setText(amountManuel);
                }
            }

            TextView currencyTextView = (TextView) rootView.findViewById(R.id.result_currency);
            currencyTextView.setText(result.getCurrency());

            TextView referenceTextView = (TextView) rootView.findViewById(R.id.result_reference_number);
            SpannableString content = new SpannableString(result.getReference());
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
            referenceTextView.setText(content);

            final GetSendServiceCallback getSendServiceCallback = (GetSendServiceCallback) getActivity();
            referenceTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    send(SEND_COMPONENT_REFERENCE, getSendServiceCallback.getEsrSender());
                }
            });

            reasonTextView.setVisibility(View.GONE);
            reasonEditText.setVisibility(View.GONE);
        } else if (psResult instanceof EsResult) {
            EsResult result = (EsResult) psResult;

            TextView currencyTextView = (TextView) rootView.findViewById(R.id.result_currency);
            currencyTextView.setText("CHF");

            amountTextView.setVisibility(View.GONE);
            amountEditText.setVisibility(View.VISIBLE);
            String amountManuel = historyItem.getAmount();
            if (amountManuel == null || amountManuel == "" || amountManuel.length() == 0) {
                amountEditText.setText(R.string.result_amount_not_set);
                amountEditText.selectAll();
            } else {
                amountEditText.setText(amountManuel);
            }

            TextView referenceTextView = (TextView) rootView.findViewById(R.id.result_reference_number);
            referenceTextView.setText(result.getReference());

            reasonTextView.setVisibility(View.VISIBLE);
            reasonEditText.setVisibility(View.VISIBLE);

            reasonEditText.setText(result.getReason());
        }

        String dtaFilename = historyItem.getDTAFilename();

        TextView dtaFilenameTextView = (TextView) rootView.findViewById(R.id.result_dta_file);
        dtaFilenameTextView.setOnClickListener(exportAgainListener);

        TextView dtaFilenameTextTextView = (TextView) rootView.findViewById(R.id.result_dta_file_text);

        if (dtaFilename != null && dtaFilename != "") {
            SpannableString content = new SpannableString(historyItem.getDTAFilename());
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
            dtaFilenameTextView.setText(content);
            dtaFilenameTextTextView.setVisibility(View.VISIBLE);
        } else {
            dtaFilenameTextTextView.setVisibility(View.GONE);
            dtaFilenameTextView.setText("");
        }

        ImageButton addressChangeButton = (ImageButton) rootView.findViewById(R.id.button_address_change);
        addressChangeButton.setOnClickListener(addressChangeListener);
        addressChangeButton.setVisibility(View.VISIBLE);

        EditText addressEditText = (EditText) rootView.findViewById(R.id.result_address);
        addressEditText.setText("");

        if (historyItem.getAddressId() != -1) {
            addressEditText.setText(historyItem.getAddress());
        } else {
            showAddressDialog(rootView);
        }

        return rootView;
    }

    public HistoryItem getHistoryItem() {
        return historyItem;
    }

    public int getListPosition() {
        return listPosition;
    }

    public int save() {

        if (historyItem == null || historyItem.getResult() == null) {
            return 0;
        }

        PsResult result = PsResult.getInstance(historyItem.getResult().getCompleteCode());

        if (result instanceof EsResult || TextUtils.isEmpty(((EsrResult) result).getAmount())) {
            EditText amountEditText = (EditText) getView().findViewById(R.id.result_amount_edit);
            String newAmount = amountEditText.getText().toString().replace(',', '.');
            try {
                float newAmountTemp = Float.parseFloat(newAmount);
                newAmountTemp *= 100;
                newAmountTemp -= (newAmountTemp % 5);
                newAmountTemp /= 100;

                newAmount = String.valueOf(newAmountTemp);

                if (newAmount.indexOf('.') == newAmount.length() - 2) {
                    newAmount += "0";
                }

                if (historyManager == null) {
                    Log.e(TAG, "onClick: historyManager is null!");
                    return 0;
                }

                historyManager.updateHistoryItemAmount(historyItem.getResult().getCompleteCode(),
                        newAmount);
                amountEditText.setText(newAmount);

            } catch (NumberFormatException e) {
                return R.string.msg_amount_not_valid;
            }
        }

        int addressStatus = 0;
        EditText addressEditText = (EditText) getView().findViewById(R.id.result_address);
        String address = addressEditText.getText().toString();
        if (address.length() > 0) {

            addressStatus = DTAFileCreator.validateAddress(address, historyItem.getResult());

            if (historyItem.getAddressId() == -1) {
                String account = historyItem.getResult().getAccount();
                int addressId = historyManager.getAddressId(account,
                        historyManager.addAddress(historyItem.getResult().getAccount(), address));
                historyManager.updateHistoryItemAddressId(historyItem.getResult().getCompleteCode(),
                        addressId);
            } else {
                historyManager.updateAddress(historyItem.getAddressId(), address);
            }
        }

        int reasonStatus = 0;
        EditText reasonEditText = (EditText) getView().findViewById(R.id.result_reason);
        String reason = reasonEditText.getText().toString();
        if (reason.length() > 0) {
            reasonStatus = DTAFileCreator.validateReason(reason);

            historyManager.updateHistoryItemReason(historyItem.getResult().getCompleteCode(),
                    reason);
        }

        return (addressStatus != 0 ? addressStatus : reasonStatus);
    }

    public void send(int sendComponent, final IEsrSender boundService) {
        send(sendComponent, boundService, -1);
    }

    public void send(int sendComponent, final IEsrSender boundService, int position) {

        String completeCode = "";
        if (sendComponent == SEND_COMPONENT_CODE_ROW) {
            completeCode = getHistoryItem().getResult().getCompleteCode();
        } else {
            if (getHistoryItem().getResult().getType().equals(EsrResult.PS_TYPE_NAME)) {
                completeCode = ((EsrResult) getHistoryItem().getResult()).getReference();
            }
        }

        int indexOfNewline = completeCode.indexOf('\n');
        if (indexOfNewline > -1) {
            completeCode = completeCode.substring(0, indexOfNewline);
        }

        boundService.sendToListener(completeCode, position);
    }

    private void showAddressDialog(View view) {
        PsResult result = historyItem.getResult();
        List<String> addresses = new ArrayList<String>();
        addresses.addAll(historyManager.getAddresses(result.getAccount()));
        addresses.add(getResources().getString(R.string.address_new));

        if (addresses.size() <= 1) {
            ImageButton addressChangeButton = (ImageButton) view.findViewById(R.id.button_address_change);
            addressChangeButton.setVisibility(View.GONE);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.address_dialog_title);
        builder.setItems(addresses.toArray(new String[addresses.size()]),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        EditText addressEditText = (EditText) getView().findViewById(R.id.result_address);
                        int addressId = historyManager.getAddressId(historyItem.getResult()
                                .getAccount(), which);
                        String address = historyManager.getAddress(addressId);

                        if (address != "") {
                            historyManager.updateHistoryItemAddressId(historyItem.getResult()
                                    .getCompleteCode(), addressId);

                            historyItem.setAddress(address);
                            historyItem.setAddressId(which);
                            addressEditText.setText(historyItem.getAddress());

                            Toast toast = Toast.makeText(getActivity(),
                                    R.string.msg_saved,
                                    Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.BOTTOM, 0, 0);
                            toast.show();
                        } else {
                            historyItem.setAddress("");
                            historyItem.setAddressId(-1);
                            addressEditText.setText(historyItem.getAddress());
                        }
                    }
                });
        builder.setNeutralButton(R.string.button_cancel, null);
        builder.show();
    }
}
