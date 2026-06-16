package com.spares.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.spares.app.R;
import com.spares.app.db.Goal;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final double TRANSFER_THRESHOLD = 50.0;
    private static final String PREFS_NAME = "spares_prefs";
    private static final String PREF_VPA   = "savings_vpa";

    private MainViewModel viewModel;
    private TransactionAdapter adapter;
    private SharedPreferences prefs;

    private TextView tvGoalTitle, tvAccumulatedAmount, tvTargetAmount;
    private TextView tvProgressLabel, tvUnsettledAmount, tvThresholdHint;
    private TextView tvLastRefresh, tvRefreshStatus, tvTxCount;
    private TextView tvMerchantInsight;
    private LinearLayout layoutMerchantInsight;
    private ProgressBar progressBar, progressSpinner;
    private Button btnTransfer;
    private AppCompatImageButton btnRefresh, btnChangeGoal;
    private LinearLayout layoutEmpty;
    private ChipGroup chipGroupCategory;

    private Goal currentGoal;
    private double unsettledTotal = 0.0;

    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean ok = Boolean.TRUE.equals(result.get(Manifest.permission.RECEIVE_SMS))
                      && Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS));
            if (ok) {
                requestBatteryOptimizationExemption();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("SMS Permission Required")
                    .setMessage("Spares needs SMS access to detect bank transactions.")
                    .setPositiveButton("Grant", (d, w) -> requestSmsPermissions())
                    .setNegativeButton("Exit", (d, w) -> finish())
                    .show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        bindViews();
        setupRecyclerView();
        setupCategoryChips();
        observeData();
        requestSmsPermissions();
    }

    private void bindViews() {
        tvGoalTitle            = findViewById(R.id.tv_goal_title);
        tvAccumulatedAmount    = findViewById(R.id.tv_accumulated_amount);
        tvTargetAmount         = findViewById(R.id.tv_target_amount);
        progressBar            = findViewById(R.id.progress_bar);
        tvProgressLabel        = findViewById(R.id.tv_progress_label);
        tvUnsettledAmount      = findViewById(R.id.tv_unsettled_amount);
        tvThresholdHint        = findViewById(R.id.tv_threshold_hint);
        tvLastRefresh          = findViewById(R.id.tv_last_refresh);
        tvRefreshStatus        = findViewById(R.id.tv_refresh_status);
        tvTxCount              = findViewById(R.id.tv_tx_count);
        progressSpinner        = findViewById(R.id.progress_spinner);
        btnTransfer            = findViewById(R.id.btn_transfer);
        btnRefresh             = findViewById(R.id.btn_refresh);
        btnChangeGoal          = findViewById(R.id.btn_change_goal);
        layoutEmpty            = findViewById(R.id.layout_empty);
        chipGroupCategory      = findViewById(R.id.chip_group_category);
        layoutMerchantInsight  = findViewById(R.id.layout_merchant_insight);
        tvMerchantInsight      = findViewById(R.id.tv_merchant_insight);

        btnTransfer.setOnClickListener(v -> showTransferSheet());

        btnRefresh.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.isRefreshing.getValue())) return;
            viewModel.refreshFromSmsInbox();
        });

        btnChangeGoal.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Change Goal?")
                .setMessage("This will deactivate the current goal and start a new one.")
                .setPositiveButton("Change", (d, w) -> {
                    startActivity(new Intent(this, GoalSetupActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show()
        );
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        RecyclerView rv = findViewById(R.id.rv_transactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    private void setupCategoryChips() {
        chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String cat;
            if (id == R.id.chip_debits) {
                cat = "DEBIT";
            } else if (id == R.id.chip_credits) {
                cat = "CREDIT";
            } else if (id == R.id.chip_transfers) {
                cat = "TRANSFER";
            } else {
                cat = "ALL";
            }
            viewModel.setCategory(cat);
        });
    }

    private void observeData() {
        viewModel.activeGoal.observe(this, goal -> {
            currentGoal = goal;
            if (goal == null) {
                startActivity(new Intent(this, GoalSetupActivity.class));
                finish();
                return;
            }
            updateGoalUI(goal);
        });

        viewModel.filteredTransactions.observe(this, txs -> {
            adapter.submitList(txs);
            int count = txs != null ? txs.size() : 0;
            layoutEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            tvTxCount.setText(count > 0 ? count + " transactions" : "");
        });

        viewModel.unsettledTotal.observe(this, total -> {
            unsettledTotal = total != null ? total : 0.0;
            tvUnsettledAmount.setText(
                String.format(Locale.getDefault(), "₹%.2f", unsettledTotal));
            updateTransferButton();
        });

        viewModel.isRefreshing.observe(this, refreshing -> {
            progressSpinner.setVisibility(refreshing ? View.VISIBLE : View.GONE);
            btnRefresh.setEnabled(!refreshing);
            btnRefresh.setAlpha(refreshing ? 0.4f : 1.0f);
            if (refreshing) tvLastRefresh.setText("Scanning inbox…");
        });

        viewModel.lastRefreshStatus.observe(this, status -> {
            if (status == null || status.isEmpty()) return;
            tvLastRefresh.setText("Last scan: just now");
            tvRefreshStatus.setText(status);
            tvRefreshStatus.setVisibility(View.VISIBLE);
            tvRefreshStatus.postDelayed(() -> tvRefreshStatus.setVisibility(View.GONE), 4000);
        });

        viewModel.topMerchantInsight.observe(this, insight -> {
            if (insight == null || insight.isEmpty()) {
                layoutMerchantInsight.setVisibility(View.GONE);
            } else {
                tvMerchantInsight.setText(insight);
                layoutMerchantInsight.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateGoalUI(Goal goal) {
        tvGoalTitle.setText(goal.title);
        tvAccumulatedAmount.setText(
            String.format(Locale.getDefault(), "₹%.2f", goal.currentAccumulated));
        tvTargetAmount.setText(
            String.format(Locale.getDefault(), "of ₹%.0f", goal.targetAmount));

        int percent = (goal.targetAmount > 0)
            ? (int) Math.min(100, (goal.currentAccumulated / goal.targetAmount) * 100)
            : 0;
        progressBar.setProgress(percent);
        tvProgressLabel.setText(percent + "% complete");
        updateTransferButton();
    }

    private void updateTransferButton() {
        boolean canTransfer = unsettledTotal >= TRANSFER_THRESHOLD;
        btnTransfer.setEnabled(canTransfer);
        if (canTransfer) {
            btnTransfer.setText(String.format(Locale.getDefault(),
                "Transfer ₹%.2f to Savings →", unsettledTotal));
            tvThresholdHint.setVisibility(View.GONE);
        } else {
            btnTransfer.setText("Transfer to Savings");
            double need = Math.max(0, TRANSFER_THRESHOLD - unsettledTotal);
            tvThresholdHint.setText(String.format(Locale.getDefault(), "₹%.0f more to unlock", need));
            tvThresholdHint.setVisibility(View.VISIBLE);
        }
    }

    // ── Transfer Bottom Sheet ────────────────────────────────────────────────

    private void showTransferSheet() {
        if (currentGoal == null) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_transfer, null);
        sheet.setContentView(sheetView);

        TextView tvAmount      = sheetView.findViewById(R.id.bs_tv_amount);
        TextView tvVpa         = sheetView.findViewById(R.id.bs_tv_vpa);
        TextView tvChangeVpa   = sheetView.findViewById(R.id.bs_tv_change_vpa);
        Button btnOpenUpi      = sheetView.findViewById(R.id.bs_btn_open_upi);
        Button btnMarkDone     = sheetView.findViewById(R.id.bs_btn_mark_done);

        tvAmount.setText(String.format(Locale.getDefault(), "₹%.2f", unsettledTotal));
        String storedVpa = prefs.getString(PREF_VPA, "");
        tvVpa.setText(storedVpa.isEmpty() ? "Tap 'Change' to add UPI ID" : storedVpa);

        tvChangeVpa.setOnClickListener(v ->
            promptForVpa(newVpa -> tvVpa.setText(newVpa))
        );

        btnOpenUpi.setOnClickListener(v -> {
            String vpa = prefs.getString(PREF_VPA, "").trim();
            if (vpa.isEmpty()) {
                promptForVpa(newVpa -> {
                    tvVpa.setText(newVpa);
                    fireUpiIntent(newVpa);
                    switchSheetToConfirmState(btnOpenUpi, btnMarkDone);
                });
            } else {
                fireUpiIntent(vpa);
                switchSheetToConfirmState(btnOpenUpi, btnMarkDone);
            }
        });

        btnMarkDone.setOnClickListener(v -> {
            viewModel.settleAll(() -> runOnUiThread(() -> {
                Toast.makeText(this,
                    String.format(Locale.getDefault(), "₹%.2f marked as transferred!", unsettledTotal),
                    Toast.LENGTH_SHORT).show();
                viewModel.computeMerchantInsight();
            }));
            sheet.dismiss();
        });

        sheet.show();
    }

    private void switchSheetToConfirmState(Button btnOpenUpi, Button btnMarkDone) {
        btnOpenUpi.setText("Opened UPI App");
        btnOpenUpi.setEnabled(false);
        btnOpenUpi.setAlpha(0.5f);
        btnMarkDone.setVisibility(View.VISIBLE);
    }

    private void fireUpiIntent(String vpa) {
        if (TextUtils.isEmpty(vpa)) return;
        String uri = String.format(Locale.getDefault(),
            "upi://pay?pa=%s&pn=SparesGoal&am=%.2f&cu=INR&tn=Spares+Round-Up",
            vpa, unsettledTotal);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No UPI app found (install GPay or PhonePe)", Toast.LENGTH_LONG).show();
        }
    }

    private void promptForVpa(VpaCallback callback) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("yourname@okicici");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        String existing = prefs.getString(PREF_VPA, "");
        if (!existing.isEmpty()) et.setText(existing);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("Your Savings UPI ID")
            .setMessage("Round-ups will be transferred here")
            .setView(et)
            .setPositiveButton("Save", (d, w) -> {
                String vpa = et.getText().toString().trim();
                if (vpa.isEmpty() || !vpa.contains("@")) {
                    Toast.makeText(this, "Enter a valid UPI ID", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString(PREF_VPA, vpa).apply();
                if (callback != null) callback.onVpa(vpa);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    interface VpaCallback { void onVpa(String vpa); }

    // ── Permissions ───────────────────────────────────────────────────────────

    private void requestSmsPermissions() {
        boolean ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                     == PackageManager.PERMISSION_GRANTED
                  && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                     == PackageManager.PERMISSION_GRANTED;
        if (ok) { requestBatteryOptimizationExemption(); return; }

        new AlertDialog.Builder(this)
            .setTitle("SMS Access Required")
            .setMessage("Spares reads bank SMS locally on your device to detect transactions. Nothing leaves your phone.")
            .setPositiveButton("Continue", (d, w) -> permissionLauncher.launch(new String[]{
                Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS
            }))
            .setCancelable(false)
            .show();
    }

    @SuppressWarnings("BatteryLife")
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }
    }
}
