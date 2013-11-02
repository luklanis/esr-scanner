package ch.luklanis.esscan.history;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.codesend.GetSendServiceCallback;
import ch.luklanis.esscan.codesend.IEsrSender;
import ch.luklanis.esscan.paymentslip.DTAFileCreator;
import ch.luklanis.esscan.paymentslip.EsResult;
import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.PsResult;

public class PsDetailFragment extends Fragment {

    public static final String ARG_POSITION = "history_position";
    public static final int SEND_COMPONENT_CODE_ROW = 0;
    public static final int SEND_COMPONENT_REFERENCE = 1;
    protected static final String TAG = PsDetailFragment.class.getSimpleName();
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
            clipboardManager.setText(mHistoryItem.getResult().getCompleteCode());

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
                            mHistoryManager.updateHistoryItemFileName(mHistoryItem.getResult()
                                    .getCompleteCode(), null);

                            TextView dtaFilenameTextView = (TextView) getView().findViewById(R.id.result_dta_file);
                            dtaFilenameTextView.setText("");
                        }
                    })
                    .show();
        }
    };
    private HistoryManager mHistoryManager;
    private HistoryItem mHistoryItem;
    private int mListPosition;
    private boolean isNewBankProfile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHistoryManager = new HistoryManager(getActivity());

        if (getArguments().containsKey(ARG_POSITION)) {
            mListPosition = getArguments().getInt(ARG_POSITION);
            mHistoryItem = mHistoryManager.buildHistoryItem(mListPosition);
        } else {
            mHistoryItem = null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_ps_detail, container, false);

        if (mHistoryItem == null) {
            return rootView;
        }

        PsResult psResult = mHistoryItem.getResult();

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
                String amountManuel = mHistoryItem.getAmount();
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
            String amountManuel = mHistoryItem.getAmount();
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

        String dtaFilename = mHistoryItem.getDTAFilename();

        TextView dtaFilenameTextView = (TextView) rootView.findViewById(R.id.result_dta_file);
        dtaFilenameTextView.setOnClickListener(exportAgainListener);

        TextView dtaFilenameTextTextView = (TextView) rootView.findViewById(R.id.result_dta_file_text);

        if (dtaFilename != null && dtaFilename != "") {
            SpannableString content = new SpannableString(mHistoryItem.getDTAFilename());
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

        if (mHistoryItem.getAddressId() != -1) {
            addressEditText.setText(mHistoryItem.getAddress());
        } else {
            showAddressDialog(rootView);
        }

        setBankProfile(rootView);

        return rootView;
    }

    private void setBankProfile(View rootView) {
        TextView bankViewText = (TextView) rootView.findViewById(R.id.result_dta_bank_profile);
        bankViewText.setText(R.string.bank_profile_default);
        bankViewText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBankProfileEditDialog();
            }
        });

        if (mHistoryItem.getBankProfileId() != -1) {
            bankViewText.setText(mHistoryItem.getBankProfile().getName());
        }

        SpannableString content = new SpannableString(bankViewText.getText());
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        bankViewText.setText(content);
    }

    public HistoryItem getHistoryItem() {
        return mHistoryItem;
    }

    public int getListPosition() {
        return mListPosition;
    }

    public int save() {

        if (mHistoryItem == null || mHistoryItem.getResult() == null) {
            return 0;
        }

        PsResult result = PsResult.getInstance(mHistoryItem.getResult().getCompleteCode());

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

                if (mHistoryManager == null) {
                    Log.e(TAG, "onClick: mHistoryManager is null!");
                    return 0;
                }

                mHistoryManager.updateHistoryItemAmount(mHistoryItem.getResult().getCompleteCode(),
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

            addressStatus = DTAFileCreator.validateAddress(address, mHistoryItem.getResult());

            if (mHistoryItem.getAddressId() == -1) {
                String account = mHistoryItem.getResult().getAccount();
                int addressId = mHistoryManager.getAddressId(account,
                        mHistoryManager.addAddress(mHistoryItem.getResult().getAccount(), address));
                mHistoryManager.updateHistoryItemAddressId(mHistoryItem.getResult()
                        .getCompleteCode(), addressId);
            } else {
                mHistoryManager.updateAddress(mHistoryItem.getAddressId(), address);
            }
        }

        int reasonStatus = 0;
        EditText reasonEditText = (EditText) getView().findViewById(R.id.result_reason);
        String reason = reasonEditText.getText().toString();
        if (reason.length() > 0) {
            reasonStatus = DTAFileCreator.validateReason(reason);

            mHistoryManager.updateHistoryItemReason(mHistoryItem.getResult().getCompleteCode(),
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
        PsResult result = mHistoryItem.getResult();
        List<String> addresses = new ArrayList<String>();
        addresses.addAll(mHistoryManager.getAddresses(result.getAccount()));

        if (addresses.size() <= 0) {
            ImageButton addressChangeButton = (ImageButton) view.findViewById(R.id.button_address_change);
            addressChangeButton.setVisibility(View.GONE);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.address_dialog_title)
                .setItems(addresses.toArray(new String[addresses.size()]), new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                EditText addressEditText = (EditText) getView().findViewById(R.id.result_address);
                                int addressId = mHistoryManager.getAddressId(mHistoryItem.getResult()
                                        .getAccount(), which);
                                String address = mHistoryManager.getAddress(addressId);

                                if (address != "") {
                                    mHistoryManager.updateHistoryItemAddressId(mHistoryItem.getResult()
                                            .getCompleteCode(), addressId);

                                    mHistoryItem.setAddress(address);
                                    mHistoryItem.setAddressId(addressId);
                                    addressEditText.setText(mHistoryItem.getAddress());

                                    Toast toast = Toast.makeText(getActivity(),
                                            R.string.msg_saved,
                                            Toast.LENGTH_SHORT);
                                    toast.setGravity(Gravity.BOTTOM, 0, 0);
                                    toast.show();
                                }
                            }
                        })
                .setNeutralButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.address_new, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mHistoryItem.setAddress("");
                        mHistoryItem.setAddressId(-1);
                        EditText addressEditText = (EditText) getView().findViewById(R.id.result_address);
                        addressEditText.setText(mHistoryItem.getAddress());
                    }
                })
                .show();
    }

    private void showBankProfileEditDialog() {
        BankProfile bankProfile = mHistoryItem.getBankProfile();

        if (!isNewBankProfile && mHistoryItem.getBankProfileId() == -1) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            bankProfile = new BankProfile(getResources().getString(R.string.bank_profile_default),
                    prefs.getString(PreferencesActivity.KEY_IBAN, ""),
                    prefs.getString(PreferencesActivity.KEY_EXECUTION_DAY, "26"));
        }

        final BankProfileDialogFragment bankProfileDialogFragment = isNewBankProfile ? new BankProfileDialogFragment() : new BankProfileDialogFragment(
                bankProfile);
        bankProfileDialogFragment.setOnChooseBankClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                showBankProfileChoiceDialog();
            }
        });
        bankProfileDialogFragment.setOnSaveBankClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                BankProfile bankProfile = bankProfileDialogFragment.getBankProfile();
                mHistoryItem.setBankProfile(bankProfile);

                if (isNewBankProfile) {
                    isNewBankProfile = false;
                    int bankProfileId = mHistoryManager.addBankProfile(bankProfile);
                    mHistoryManager.updateHistoryItemBankProfileId(mHistoryItem.getResult()
                            .getCompleteCode(), bankProfileId);
                    mHistoryItem.setBankProfileId(bankProfileId);
                } else if (mHistoryItem.getBankProfileId() == -1) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                            getActivity());
                    prefs.edit()
                            .putString(PreferencesActivity.KEY_IBAN, bankProfile.getIban(""))
                            .putString(PreferencesActivity.KEY_EXECUTION_DAY,
                                    String.valueOf(bankProfile.getExecutionDay(26)))
                            .apply();
                } else {
                    mHistoryManager.updateBankProfile(mHistoryItem.getBankProfileId(), bankProfile);
                }

                setBankProfile(getView());
            }
        });
        bankProfileDialogFragment.setOnCancelClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (isNewBankProfile) {
                    isNewBankProfile = false;
                }
            }
        });
        bankProfileDialogFragment.show(getFragmentManager(), "bankProfile");
    }

    private void showBankProfileChoiceDialog() {
        List<String> jsonBankProfiles = mHistoryManager.getAddresses("BP");

        List<String> banks = new ArrayList<String>();

        for (String json : jsonBankProfiles) {
            banks.add(new BankProfile(json).getName());
        }
        banks.add(0, getResources().getString(R.string.bank_profile_default));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.bank_profile_dialog_title)
                .setItems(banks.toArray(new String[banks.size()]), new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                TextView bankProfileTextView = (TextView) getView().findViewById(R.id.result_dta_bank_profile);

                                int bankProfileId = -1;
                                if (which != 0) {

                                    bankProfileId = mHistoryManager.getAddressId("BP", which - 1);
                                    BankProfile bankProfile = mHistoryManager.getBankProfile(
                                            bankProfileId);

                                    if (bankProfile == null) {
                                        return;
                                    }

                                    mHistoryItem.setBankProfile(bankProfile);
                                    bankProfileTextView.setText(mHistoryItem.getBankProfile()
                                            .getName());
                                } else {
                                    bankProfileTextView.setText(R.string.bank_profile_default);
                                }

                                mHistoryManager.updateHistoryItemBankProfileId(mHistoryItem.getResult()
                                        .getCompleteCode(), bankProfileId);

                                mHistoryItem.setBankProfileId(bankProfileId);

                                Toast toast = Toast.makeText(getActivity(),
                                        R.string.msg_saved,
                                        Toast.LENGTH_SHORT);
                                toast.setGravity(Gravity.BOTTOM, 0, 0);
                                toast.show();
                            }
                        })
                .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showBankProfileEditDialog();
                    }
                })
                .setNeutralButton(R.string.button_create_new,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                isNewBankProfile = true;
                                showBankProfileEditDialog();
                            }
                        })
                .show();
    }
}
