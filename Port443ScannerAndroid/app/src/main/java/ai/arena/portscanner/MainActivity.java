package ai.arena.portscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_FILE = 1001;
    private static final int REQ_SAVE_CSV = 1002;
    private static final int MAX_DISPLAY_ROWS = 180;

    private final int BG = Color.rgb(11, 15, 23);
    private final int SURFACE = Color.rgb(21, 27, 38);
    private final int CARD = Color.rgb(28, 35, 50);
    private final int BORDER = Color.rgb(42, 51, 67);
    private final int TEXT = Color.rgb(238, 242, 248);
    private final int TEXT2 = Color.rgb(148, 163, 184);
    private final int PRIMARY = Color.rgb(109, 40, 217);
    private final int BLUE = Color.rgb(79, 70, 229);
    private final int SUCCESS = Color.rgb(16, 185, 129);
    private final int DANGER = Color.rgb(239, 68, 68);
    private final int WARNING = Color.rgb(245, 158, 11);

    private EditText ipInput, rangeInput, timeoutInput, concurrencyInput, attemptsInput, minOkInput, sniInput, portInput;
    private EditText speedHostInput, downloadPathInput, downloadKbInput, uploadPathInput, uploadKbInput;
    private EditText frontSniInput, frontHostInput, frontPathInput, expectedStatusInput, expectedMarkerInput, frontKbInput, frontRoundsInput, verifyNamesInput, copyTopInput;
    private EditText deepRoundsInput, deepDelayInput;
    private Spinner modeSpinner;
    private CheckBox skipPrivateBox, speedTestBox, uploadTestBox, frontingModeBox, advancedModeBox, deepAnalysisBox;
    private Button prepareBtn, startBtn, stopBtn, copyBtn, saveBtn, importBtn, generateBtn, sendGeneratedBtn, shareBtn, clearBtn;
    private TextView totalText, scannedText, openText, failText, maybeText, resultText, rangeCountText, modeHelpText, speedText, etaText, analyticsText;
    private ProgressBar progressBar;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicInteger index = new AtomicInteger(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final AtomicInteger scanned = new AtomicInteger(0);
    private final AtomicInteger opened = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger maybe = new AtomicInteger(0);
    private final AtomicLong lastUi = new AtomicLong(0);

    private List<String> currentIps = new ArrayList<>();
    private List<String> generatedIps = new ArrayList<>();
    private final List<View> advancedViews = new ArrayList<>();
    private final List<ScanResult> results = Collections.synchronizedList(new ArrayList<>());
    private String pendingCsv = "";
    private long scanStartMs = 0L;
    private volatile int scanPort = 443, scanTimeout = 3000, scanAttempts = 2, scanMinOk = 1, scanMode = 0;
    private volatile String scanSni = "";
    private volatile boolean scanSpeedEnabled = false, scanUploadEnabled = false;
    private volatile String scanSpeedHost = "", scanDownloadPath = "/", scanUploadPath = "/";
    private volatile int scanDownloadKb = 1024, scanUploadKb = 512;
    private volatile boolean scanFrontingEnabled = false;
    private volatile String scanFrontSni = "", scanFrontHost = "", scanFrontPath = "/", scanExpectedMarker = "";
    private volatile List<String> scanVerifyNames = new ArrayList<>();
    private volatile int scanExpectedStatus = 0, scanFrontKb = 512, scanFrontRounds = 1, scanCopyTopN = 0;
    private volatile boolean scanDeepEnabled = false;
    private volatile int scanDeepRounds = 5, scanDeepDelayMs = 200;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        styleSystemBars();
        buildUi();
        loadSettings();
        showSafetyDialogOnce();
    }

    @Override
    protected void onDestroy() {
        try {
            stopRequested.set(true);
            scanning.set(false);
            if (executor != null) executor.shutdownNow();
        } catch (Exception ignored) {}
        releaseWakeLock();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackground(makeGradientBg(Color.rgb(7, 11, 19), Color.rgb(15, 23, 42), 0, 0));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(26));
        scroll.addView(root);

        LinearLayout header = heroCard(root);
        LinearLayout headerTop = row();
        headerTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = tv("⚡", 34, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(makeGradientBg(Color.rgb(109,40,217), Color.rgb(16,185,129), dp(22), 0));
        if (android.os.Build.VERSION.SDK_INT >= 21) logo.setElevation(dp(8));
        headerTop.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        TextView title = tv("EdgePulse", 23, Color.WHITE, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titleBox.addView(title);
        TextView sub = tv("Native TCP · TLS · Deep Analysis", 12, Color.rgb(210, 220, 255), false);
        titleBox.addView(sub);
        headerTop.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView version = chip("v1.6 Core");
        headerTop.addView(version);
        header.addView(headerTop);
        LinearLayout chips = row();
        chips.setGravity(Gravity.START);
        chips.addView(chip("TCP واقعی"));
        chips.addView(chip("TLS + SNI"));
        chips.addView(chip("Mbps Rank"));
        header.addView(chips);
        TextView desc = tv("پیدا کردن IPهای باز، تحلیل پایداری، امتیازدهی هوشمند و خروجی CSV.", 12, Color.rgb(226,232,240), false);
        desc.setPadding(0, dp(10), 0, 0);
        header.addView(desc);
        advancedModeBox = new CheckBox(this);
        advancedModeBox.setText("Advanced Mode");
        advancedModeBox.setTextColor(Color.WHITE);
        advancedModeBox.setTextSize(12);
        advancedModeBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        header.addView(advancedModeBox, matchLp());

        deepAnalysisBox = new CheckBox(this);
        deepAnalysisBox.setText("Deep Analysis فقط روی Candidateها");
        deepAnalysisBox.setTextColor(Color.WHITE);
        deepAnalysisBox.setTextSize(12);
        deepAnalysisBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        header.addView(deepAnalysisBox, matchLp());
        TextView deepNote = small("Deep Analysis پایداری، جیتِر و افت کیفیت را فقط روی IPهای باز می‌سنجد.");
        header.addView(deepNote);
        LinearLayout deepRow = row();
        deepRoundsInput = editNumber("5");
        deepDelayInput = editNumber("200");
        deepRow.addView(field("Deep Rounds", deepRoundsInput), weightLp());
        deepRow.addView(field("Deep Delay ms", deepDelayInput), weightLp());
        header.addView(deepRow);

        LinearLayout inputCard = card(root);
        inputCard.addView(label("📂 لیست هدف"));
        ipInput = editMultiline("1.1.1.1\n8.8.8.8\n104.16.0.0/24\n192.168.1.1-192.168.1.10", 6);
        inputCard.addView(ipInput);
        LinearLayout row1 = row();
        importBtn = btn("📄 وارد کردن فایل", BLUE);
        prepareBtn = btn("📋 آماده‌سازی", BLUE);
        row1.addView(importBtn, weightLp());
        row1.addView(prepareBtn, weightLp());
        inputCard.addView(row1);

        TextView settingsLabel = label("⚙️ تنظیمات اسکن");
        inputCard.addView(settingsLabel);
        advancedViews.add(settingsLabel);
        LinearLayout grid0 = row();
        portInput = editNumber("443");
        timeoutInput = editNumber("3000");
        grid0.addView(field("پورت", portInput), weightLp());
        grid0.addView(field("تایم‌اوت ms", timeoutInput), weightLp());
        inputCard.addView(grid0);
        advancedViews.add(grid0);
        LinearLayout grid1 = row();
        concurrencyInput = editNumber("80");
        skipPrivateBox = new CheckBox(this);
        skipPrivateBox.setText("حذف IPهای خصوصی/لوکال");
        skipPrivateBox.setTextColor(TEXT2);
        skipPrivateBox.setTextSize(12);
        skipPrivateBox.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        grid1.addView(field("همزمانی", concurrencyInput), weightLp());
        grid1.addView(skipPrivateBox, weightLp());
        inputCard.addView(grid1);
        advancedViews.add(grid1);
        LinearLayout grid2 = row();
        attemptsInput = editNumber("2");
        minOkInput = editNumber("1");
        grid2.addView(field("تلاش برای هر IP", attemptsInput), weightLp());
        grid2.addView(field("حداقل موفق", minOkInput), weightLp());
        inputCard.addView(grid2);
        advancedViews.add(grid2);

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"TCP Connect 443 - سریع و مناسب پورت", "TLS Handshake 443 - فیلتر بهتر", "TCP + TLS - سخت‌گیرانه"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        inputCard.addView(modeSpinner, matchLp());
        advancedViews.add(modeSpinner);
        sniInput = editOneLine("اختیاری: SNI Host، مثلا example.com");
        inputCard.addView(sniInput);
        advancedViews.add(sniInput);
        modeHelpText = small("حالت TCP برای تشخیص باز بودن پورت بهتر است. TLS فقط وقتی لازم است که بخواهی سرور واقعاً TLS جواب بدهد.");
        inputCard.addView(modeHelpText);
        advancedViews.add(modeHelpText);

        TextView speedLabel = label("🚀 تست سرعت واقعی");
        inputCard.addView(speedLabel);
        advancedViews.add(speedLabel);
        speedTestBox = new CheckBox(this);
        speedTestBox.setText("فعال کردن تست دانلود");
        speedTestBox.setTextColor(TEXT2);
        speedTestBox.setTextSize(12);
        speedTestBox.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        inputCard.addView(speedTestBox, matchLp());
        advancedViews.add(speedTestBox);
        speedHostInput = editOneLine("دامنه/Host برای تست سرعت؛ مثال: example.com");
        inputCard.addView(speedHostInput);
        advancedViews.add(speedHostInput);
        LinearLayout speedRow1 = row();
        downloadPathInput = editOneLine("مسیر فایل دانلود؛ مثال: /speedtest.bin یا /");
        downloadPathInput.setText("/");
        downloadKbInput = editNumber("1024");
        speedRow1.addView(field("مسیر دانلود", downloadPathInput), weightLp());
        speedRow1.addView(field("حجم دانلود KB", downloadKbInput), weightLp());
        inputCard.addView(speedRow1);
        advancedViews.add(speedRow1);
        uploadTestBox = new CheckBox(this);
        uploadTestBox.setText("فعال کردن تست آپلود - نیازمند endpoint مجاز برای POST");
        uploadTestBox.setTextColor(TEXT2);
        uploadTestBox.setTextSize(12);
        uploadTestBox.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        inputCard.addView(uploadTestBox, matchLp());
        advancedViews.add(uploadTestBox);
        LinearLayout speedRow2 = row();
        uploadPathInput = editOneLine("مسیر آپلود؛ مثال: /upload یا /speedtest/upload");
        uploadPathInput.setText("/");
        uploadKbInput = editNumber("512");
        speedRow2.addView(field("مسیر آپلود", uploadPathInput), weightLp());
        speedRow2.addView(field("حجم آپلود KB", uploadKbInput), weightLp());
        inputCard.addView(speedRow2);
        advancedViews.add(speedRow2);
        TextView speedNote = small("برای CDN/Edge، بهترین نتیجه وقتی است که Host/SNI دامنه‌ای باشد که اجازه تست آن را دارید و روی آن فایل تست واقعی وجود دارد.");
        inputCard.addView(speedNote);
        advancedViews.add(speedNote);

        LinearLayout row2 = row();
        startBtn = btn("▶ شروع", SUCCESS);
        stopBtn = btn("⏹ توقف", DANGER);
        stopBtn.setEnabled(false);
        row2.addView(startBtn, weightLp());
        row2.addView(stopBtn, weightLp());
        inputCard.addView(row2);

        LinearLayout frontCard = card(root);
        frontCard.addView(label("🎯 حالت Pair / Fronting Test"));
        frontingModeBox = new CheckBox(this);
        frontingModeBox.setText("فعال کردن تست ترکیب IP + SNI + Host");
        frontingModeBox.setTextColor(TEXT2);
        frontingModeBox.setTextSize(12);
        frontingModeBox.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        frontCard.addView(frontingModeBox, matchLp());
        LinearLayout frow1 = row();
        frontSniInput = editOneLine("Front SNI؛ مثلا دامنه مجاز خودتان");
        frontHostInput = editOneLine("HTTP Host؛ دامنه/هاست مورد تست");
        frow1.addView(field("Front SNI", frontSniInput), weightLp());
        frow1.addView(field("HTTP Host", frontHostInput), weightLp());
        frontCard.addView(frow1);
        LinearLayout frow2 = row();
        frontPathInput = editOneLine("/health.txt یا /speedtest.bin");
        frontPathInput.setText("/");
        expectedStatusInput = editNumber("0");
        frow2.addView(field("Test Path", frontPathInput), weightLp());
        frow2.addView(field("Expected Status؛ 0=2xx/3xx", expectedStatusInput), weightLp());
        frontCard.addView(frow2);
        expectedMarkerInput = editOneLine("Expected Marker اختیاری؛ متن داخل پاسخ صحیح");
        frontCard.addView(expectedMarkerInput);
        advancedViews.add(expectedMarkerInput);
        verifyNamesInput = editOneLine("Verify Names اختیاری؛ با کاما جدا کن. خالی = SNI و Host");
        frontCard.addView(verifyNamesInput);
        advancedViews.add(verifyNamesInput);
        LinearLayout frow3 = row();
        frontKbInput = editNumber("512");
        frontRoundsInput = editNumber("1");
        frow3.addView(field("حجم تست KB", frontKbInput), weightLp());
        frow3.addView(field("Rounds", frontRoundsInput), weightLp());
        frontCard.addView(frow3);
        LinearLayout frow4 = row();
        copyTopInput = editNumber("0");
        frow4.addView(field("Copy Top N؛ 0=همه", copyTopInput), weightLp());
        frontCard.addView(frow4);
        advancedViews.add(frow4);
        advancedViews.add(frow3);
        TextView frontNote = small("این حالت به‌جای IP خام، ترکیب IP+SNI+Host+Path را اعتبارسنجی و امتیازدهی می‌کند. فقط برای دامنه/زیرساخت مجاز خودتان استفاده کنید.");
        frontCard.addView(frontNote);
        advancedViews.add(frontNote);

        LinearLayout genCard = card(root);
        advancedViews.add(genCard);
        genCard.addView(label("🌐 تولید لیست از CIDR / بازه"));
        rangeInput = editMultiline("104.16.0.0/24\n172.64.0.1-172.64.0.50", 3);
        genCard.addView(rangeInput);
        LinearLayout row3 = row();
        generateBtn = btn("⚙️ تولید", BLUE);
        sendGeneratedBtn = btn("📤 ارسال به اسکنر", SUCCESS);
        row3.addView(generateBtn, weightLp());
        row3.addView(sendGeneratedBtn, weightLp());
        genCard.addView(row3);
        rangeCountText = small("تولید شده: ۰");
        genCard.addView(rangeCountText);

        genCard.addView(label("🛰️ Presetهای CDN"));
        TextView presetNote = small("یک Preset را انتخاب کن، در ورودی بالا CIDRها قرار می‌گیرد، بعد «تولید» و «ارسال به اسکنر».");
        genCard.addView(presetNote);
        LinearLayout presetRow = row();
        Button akamaiBtn = btn("Akamai", Color.rgb(30, 41, 59));
        Button cloudflareBtn = btn("Cloudflare", Color.rgb(30, 41, 59));
        Button fastlyBtn = btn("Fastly", Color.rgb(30, 41, 59));
        presetRow.addView(akamaiBtn, weightLp());
        presetRow.addView(cloudflareBtn, weightLp());
        presetRow.addView(fastlyBtn, weightLp());
        genCard.addView(presetRow);
        akamaiBtn.setOnClickListener(v -> { rangeInput.setText(CdnPresets.AKAMAI); toast("Akamai preset"); });
        cloudflareBtn.setOnClickListener(v -> { rangeInput.setText(CdnPresets.CLOUDFLARE); toast("Cloudflare preset"); });
        fastlyBtn.setOnClickListener(v -> { rangeInput.setText(CdnPresets.FASTLY); toast("Fastly preset"); });

        LinearLayout statsCard = card(root);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        statsCard.addView(progressBar, matchLp());
        totalText = stat("کل: 0"); scannedText = stat("اسکن‌شده: 0"); openText = stat("باز: 0"); maybeText = stat("نامطمئن: 0"); failText = stat("بسته/تایم‌اوت: 0");
        statsCard.addView(totalText); statsCard.addView(scannedText); statsCard.addView(openText); statsCard.addView(maybeText); statsCard.addView(failText);
        speedText = stat("سرعت: 0 IP/s"); etaText = stat("زمان باقی‌مانده: -"); analyticsText = stat("تحلیل: آماده");
        statsCard.addView(speedText); statsCard.addView(etaText); statsCard.addView(analyticsText);

        LinearLayout actionRow = row();
        copyBtn = btn("📋 کپی بازها", Color.rgb(30, 41, 59));
        saveBtn = btn("📥 ذخیره CSV", Color.rgb(30, 41, 59));
        shareBtn = btn("📤 اشتراک", Color.rgb(30, 41, 59));
        clearBtn = btn("🧹 پاکسازی", Color.rgb(30, 41, 59));
        copyBtn.setEnabled(false); saveBtn.setEnabled(false); shareBtn.setEnabled(false);
        actionRow.addView(copyBtn, weightLp()); actionRow.addView(saveBtn, weightLp());
        root.addView(actionRow);
        LinearLayout actionRow2 = row();
        actionRow2.addView(shareBtn, weightLp()); actionRow2.addView(clearBtn, weightLp());
        root.addView(actionRow2);

        LinearLayout resultCard = card(root);
        resultCard.addView(label("📊 رتبه‌بندی نتایج"));
        resultText = tv("هنوز نتیجه‌ای ثبت نشده", 13, TEXT2, false);
        resultText.setTypeface(Typeface.MONOSPACE);
        resultText.setTextDirection(View.TEXT_DIRECTION_LTR);
        resultText.setTextIsSelectable(true);
        resultText.setGravity(Gravity.START);
        resultCard.addView(resultText);

        TextView footer = small("⚠️ فقط روی IPها و شبکه‌هایی که مالک یا مجاز به تستشان هستید استفاده کنید. برای اسکن‌های خیلی بزرگ همزمانی را پایین نگه دارید.");
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        setContentView(scroll);
        bindEvents();
        applyModeVisibility();
        updateUi(true);
    }

    private void bindEvents() {
        prepareBtn.setOnClickListener(v -> prepareList(true));
        importBtn.setOnClickListener(v -> openFile());
        startBtn.setOnClickListener(v -> startScan());
        stopBtn.setOnClickListener(v -> stopScan());
        generateBtn.setOnClickListener(v -> generateRanges());
        sendGeneratedBtn.setOnClickListener(v -> {
            if (generatedIps.isEmpty()) { toast("اول رنج را تولید کن"); return; }
            ipInput.setText(joinLines(generatedIps));
            prepareList(true);
            toast("به اسکنر ارسال شد");
        });
        copyBtn.setOnClickListener(v -> copyOpenIps());
        saveBtn.setOnClickListener(v -> saveCsv());
        shareBtn.setOnClickListener(v -> shareOpenIps());
        clearBtn.setOnClickListener(v -> { if (!scanning.get()) { resetCounters(); updateUi(true); toast("نتایج پاک شد"); } });
        advancedModeBox.setOnCheckedChangeListener((buttonView, isChecked) -> { applyModeVisibility(); saveSettings(); });
        if (deepAnalysisBox != null) deepAnalysisBox.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        speedTestBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && speedHostInput.getText().toString().trim().isEmpty() && !sniInput.getText().toString().trim().isEmpty()) {
                speedHostInput.setText(sniInput.getText().toString().trim());
            }
        });
        frontingModeBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (frontSniInput.getText().toString().trim().isEmpty()) frontSniInput.setText(sniInput.getText().toString().trim());
                if (frontHostInput.getText().toString().trim().isEmpty()) frontHostInput.setText(speedHostInput.getText().toString().trim());
            }
            applyModeVisibility();
        });
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) modeHelpText.setText("TCP Connect: دقیق‌ترین حالت برای خودِ باز بودن پورت ۴۴۳.");
                if (position == 1) modeHelpText.setText("TLS Handshake: فقط IPهایی را بهتر نشان می‌دهد که واقعاً TLS جواب می‌دهند. SNI اختیاری است.");
                if (position == 2) modeHelpText.setText("TCP+TLS: سخت‌گیرانه‌تر، false positive کمتر ولی احتمال حذف IPهای قابل اتصال بیشتر.");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showSafetyDialogOnce() {
        new AlertDialog.Builder(this)
                .setTitle("استفاده مجاز")
                .setMessage("این ابزار برای بررسی پورت ۴۴۳ روی IPها/رنج‌هایی است که مالک آن هستید یا اجازه تست دارید. از اسکن گسترده و بدون مجوز خودداری کنید.")
                .setPositiveButton("متوجه شدم", null)
                .show();
    }

    private void prepareList(boolean notify) {
        if (scanning.get()) return;
        saveSettings();
        List<String> parsed = parseInput(ipInput.getText().toString(), true);
        if (skipPrivateBox != null && skipPrivateBox.isChecked()) parsed = filterPublicIps(parsed);
        currentIps = parsed;
        resetCounters();
        if (notify) toast(parsed.size() + " IP آماده شد");
        updateUi(true);
    }

    private void startScan() {
        if (scanning.get()) return;
        if (currentIps.isEmpty()) prepareList(false);
        if (currentIps.isEmpty()) { toast("لیست IP خالی است"); return; }
        if (frontingModeBox != null && frontingModeBox.isChecked()) {
            String fs = frontSniInput == null ? "" : frontSniInput.getText().toString().trim();
            String fh = frontHostInput == null ? "" : frontHostInput.getText().toString().trim();
            if (fs.isEmpty() && fh.isEmpty()) { toast("در حالت Pair Test حداقل Front SNI یا HTTP Host لازم است"); return; }
        }

        int concurrency = clamp(toInt(concurrencyInput.getText().toString(), 80), 1, 400);
        int total = currentIps.size();
        if (total > 100000) {
            new AlertDialog.Builder(this)
                    .setTitle("لیست بزرگ")
                    .setMessage("تعداد IP زیاد است (" + total + "). برای موبایل بهتر است لیست را کوچک‌تر کنید. ادامه می‌دهید؟")
                    .setPositiveButton("ادامه", (d, w) -> doStart(concurrency))
                    .setNegativeButton("لغو", null)
                    .show();
        } else {
            doStart(concurrency);
        }
    }

    private void doStart(int concurrency) {
        saveSettings();
        scanPort = clamp(toInt(portInput.getText().toString(), 443), 1, 65535);
        scanTimeout = clamp(toInt(timeoutInput.getText().toString(), 3000), 300, 20000);
        scanAttempts = clamp(toInt(attemptsInput.getText().toString(), 2), 1, 5);
        scanMinOk = clamp(toInt(minOkInput.getText().toString(), 1), 1, scanAttempts);
        scanMode = modeSpinner.getSelectedItemPosition();
        scanSni = sniInput.getText().toString().trim();
        scanSpeedEnabled = speedTestBox != null && speedTestBox.isChecked();
        scanUploadEnabled = uploadTestBox != null && uploadTestBox.isChecked();
        scanSpeedHost = speedHostInput == null ? "" : speedHostInput.getText().toString().trim();
        if (scanSpeedHost.isEmpty()) scanSpeedHost = scanSni;
        scanDownloadPath = normalizePath(downloadPathInput == null ? "/" : downloadPathInput.getText().toString().trim());
        scanUploadPath = normalizePath(uploadPathInput == null ? "/" : uploadPathInput.getText().toString().trim());
        scanDownloadKb = clamp(toInt(downloadKbInput == null ? "1024" : downloadKbInput.getText().toString(), 1024), 64, 51200);
        scanUploadKb = clamp(toInt(uploadKbInput == null ? "512" : uploadKbInput.getText().toString(), 512), 64, 51200);
        scanFrontingEnabled = frontingModeBox != null && frontingModeBox.isChecked();
        scanFrontSni = frontSniInput == null ? "" : frontSniInput.getText().toString().trim();
        scanFrontHost = frontHostInput == null ? "" : frontHostInput.getText().toString().trim();
        scanFrontPath = normalizePath(frontPathInput == null ? "/" : frontPathInput.getText().toString().trim());
        scanExpectedStatus = clamp(toInt(expectedStatusInput == null ? "0" : expectedStatusInput.getText().toString(), 0), 0, 599);
        scanExpectedMarker = expectedMarkerInput == null ? "" : expectedMarkerInput.getText().toString();
        scanVerifyNames = parseVerifyNames(verifyNamesInput == null ? "" : verifyNamesInput.getText().toString(), scanFrontSni, scanFrontHost);
        scanFrontKb = clamp(toInt(frontKbInput == null ? "512" : frontKbInput.getText().toString(), 512), 32, 51200);
        scanFrontRounds = clamp(toInt(frontRoundsInput == null ? "1" : frontRoundsInput.getText().toString(), 1), 1, 5);
        scanCopyTopN = clamp(toInt(copyTopInput == null ? "0" : copyTopInput.getText().toString(), 0), 0, 10000);
        scanDeepEnabled = deepAnalysisBox != null && deepAnalysisBox.isChecked();
        scanDeepRounds = clamp(toInt(deepRoundsInput == null ? "5" : deepRoundsInput.getText().toString(), 5), 2, 50);
        scanDeepDelayMs = clamp(toInt(deepDelayInput == null ? "200" : deepDelayInput.getText().toString(), 200), 0, 5000);
        if (scanFrontingEnabled) {
            if (scanFrontSni.isEmpty()) scanFrontSni = scanSni;
            if (scanFrontHost.isEmpty()) scanFrontHost = scanSpeedHost;
            if (scanFrontHost.isEmpty()) scanFrontHost = scanFrontSni;
        }
        resetCounters();
        scanStartMs = System.currentTimeMillis();
        scanning.set(true);
        stopRequested.set(false);
        index.set(0);
        activeWorkers.set(concurrency);
        executor = Executors.newFixedThreadPool(concurrency);
        acquireWakeLock();
        setControls(false);
        updateUi(true);
        toast("اسکن شروع شد");

        for (int i = 0; i < concurrency; i++) {
            executor.execute(() -> {
                try {
                    while (scanning.get() && !stopRequested.get()) {
                        int idx = index.getAndIncrement();
                        if (idx >= currentIps.size()) break;
                        ScanResult r = scanIp(currentIps.get(idx));
                        results.add(r);
                        scanned.incrementAndGet();
                        if (r.level == 2) opened.incrementAndGet();
                        else if (r.level == 1) maybe.incrementAndGet();
                        else failed.incrementAndGet();
                        long now = System.currentTimeMillis();
                        if (now - lastUi.get() > 250) {
                            lastUi.set(now);
                            main.post(() -> updateUi(false));
                        }
                    }
                } finally {
                    if (activeWorkers.decrementAndGet() == 0) {
                        main.post(this::finishScan);
                    }
                }
            });
        }
    }

    private void stopScan() {
        stopRequested.set(true);
        scanning.set(false);
        if (executor != null) executor.shutdownNow();
        releaseWakeLock();
        setControls(true);
        updateUi(true);
        toast("متوقف شد");
    }

    private void finishScan() {
        scanning.set(false);
        setControls(true);
        updateUi(true);
        if (executor != null) executor.shutdownNow();
        releaseWakeLock();
        toast("تمام شد: باز " + opened.get() + " / نامطمئن " + maybe.get() + " / ناموفق " + failed.get());
    }

    @SuppressWarnings("WakelockTimeout")
    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgePulse:scan");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(2L * 60L * 60L * 1000L);
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {}
    }

    private ScanResult scanIp(String ip) {
        int timeout = scanTimeout;
        int attempts = scanAttempts;
        int minOk = scanMinOk;
        int mode = scanMode;
        String sni = scanSni == null ? "" : scanSni;
        int port = scanPort;
        // In Pair/Fronting mode the meaningful test is the full IP+SNI+Host+Path probe.
        // Do not pre-filter with the generic mode/SNI, otherwise a valid pair can be skipped
        // just because the generic TLS field was empty or different.
        if (scanFrontingEnabled) return scanPairIp(ip, port, timeout);
        List<Integer> rtts = new ArrayList<>();
        String lastReason = "";
        int ok = 0;

        for (int i = 0; i < attempts && !stopRequested.get(); i++) {
            Probe p;
            if (mode == 0) p = tcpProbe(ip, port, timeout);
            else if (mode == 1) p = tlsProbe(ip, port, timeout, sni);
            else {
                Probe tcp = tcpProbe(ip, port, timeout);
                if (!tcp.ok) p = tcp;
                else p = tlsProbe(ip, port, timeout, sni);
            }
            lastReason = p.reason;
            if (p.ok) { ok++; rtts.add(p.rttMs); }
        }
        int level;
        String status;
        Integer rtt = rtts.isEmpty() ? null : median(rtts);
        if (ok >= minOk) { level = 2; status = mode == 0 ? "open" : "tls-ok"; }
        else if (ok > 0) { level = 1; status = "maybe"; }
        else { level = 0; status = lastReason == null || lastReason.isEmpty() ? "failed" : lastReason; }

        Double downMbps = null;
        Double upMbps = null;
        Integer speedTtfb = null;
        Boolean frontingOk = null;
        Integer httpStatus = null;

        if (level == 2 && scanFrontingEnabled) {
            int roundsOk = 0;
            double mbpsSum = 0;
            int ttfbSum = 0;
            String lastFrontReason = "not-tested";
            Integer lastStatusCode = null;
            for (int fr = 0; fr < scanFrontRounds && !stopRequested.get(); fr++) {
                FrontingResult frs = frontingHttpProbe(ip, port, timeout, scanFrontSni, scanFrontHost, scanFrontPath, scanFrontKb * 1024, scanExpectedStatus, scanExpectedMarker, scanVerifyNames);
                lastFrontReason = frs.reason;
                lastStatusCode = frs.statusCode;
                if (frs.ok) {
                    roundsOk++;
                    mbpsSum += frs.mbps;
                    ttfbSum += frs.ttfbMs == null ? 0 : frs.ttfbMs;
                }
            }
            httpStatus = lastStatusCode;
            if (roundsOk > 0) {
                downMbps = mbpsSum / roundsOk;
                speedTtfb = ttfbSum / roundsOk;
            }
            int needFrontOk = Math.min(scanFrontRounds, Math.max(1, scanMinOk));
            if (roundsOk >= needFrontOk) {
                frontingOk = true;
                level = 2;
                status = "pair-ok:" + (httpStatus == null ? "http" : httpStatus) + ":" + roundsOk + "/" + scanFrontRounds;
            } else {
                frontingOk = false;
                level = ok > 0 ? 1 : 0;
                status = "pair-fail:" + lastFrontReason + ":" + roundsOk + "/" + scanFrontRounds;
            }
        } else if (level == 2 && scanSpeedEnabled) {
            SpeedResult down = downloadSpeedProbe(ip, port, timeout, scanSpeedHost, scanDownloadPath, scanDownloadKb * 1024);
            if (down.ok) {
                downMbps = down.mbps;
                speedTtfb = down.ttfbMs;
                status = status + "+dl";
            } else {
                status = status + "+dl-fail:" + down.reason;
            }
            if (scanUploadEnabled) {
                SpeedResult up = uploadSpeedProbe(ip, port, timeout, scanSpeedHost, scanUploadPath, scanUploadKb * 1024);
                if (up.ok) {
                    upMbps = up.mbps;
                    status = status + "+ul";
                } else {
                    status = status + "+ul-fail:" + up.reason;
                }
            }
        }
        StabilityResult stability = null;
        if (level == 2 && scanDeepEnabled) {
            String stabilitySni = sni;
            if (stabilitySni.isEmpty() && scanFrontingEnabled) stabilitySni = scanFrontSni;
            stability = stabilityProbe(ip, port, timeout, stabilitySni, mode, scanDeepRounds, scanDeepDelayMs);
            status = status + (stability.ok ? "+deep:" + stability.label : "+deep-fail:" + stability.reason);
        }
        double score = calculateScore(level, rtt, speedTtfb, downMbps, upMbps, frontingOk, stability);
        return new ScanResult(ip, level, status, rtt, ok, attempts, downMbps, upMbps, speedTtfb, frontingOk, httpStatus, score, stability == null ? null : stability.label, stability == null ? null : stability.avgRttMs, stability == null ? null : stability.jitterMs, stability == null ? null : stability.lossPct, stability == null ? 0 : stability.okChecks, stability == null ? 0 : stability.totalChecks, stability == null ? 0 : stability.sessionMs);
    }


    private ScanResult scanPairIp(String ip, int port, int timeout) {
        Probe tcp = tcpProbe(ip, port, timeout);
        Integer rtt = tcp.ok ? tcp.rttMs : null;
        int roundsOk = 0;
        double mbpsSum = 0;
        int ttfbSum = 0;
        String lastReason = tcp.ok ? "not-tested" : tcp.reason;
        Integer lastStatusCode = null;
        for (int fr = 0; fr < scanFrontRounds && !stopRequested.get(); fr++) {
            FrontingResult frs = frontingHttpProbe(ip, port, timeout, scanFrontSni, scanFrontHost, scanFrontPath, scanFrontKb * 1024, scanExpectedStatus, scanExpectedMarker, scanVerifyNames);
            lastReason = frs.reason;
            lastStatusCode = frs.statusCode;
            if (frs.ok) {
                roundsOk++;
                mbpsSum += frs.mbps;
                ttfbSum += frs.ttfbMs == null ? 0 : frs.ttfbMs;
            }
        }
        int need = Math.min(scanFrontRounds, Math.max(1, scanMinOk));
        Double downMbps = roundsOk > 0 ? mbpsSum / roundsOk : null;
        Integer ttfb = roundsOk > 0 ? ttfbSum / roundsOk : null;
        boolean okPair = roundsOk >= need;
        int level = okPair ? 2 : (tcp.ok || roundsOk > 0 ? 1 : 0);
        String status = okPair
                ? ("pair-ok:" + (lastStatusCode == null ? "http" : lastStatusCode) + ":" + roundsOk + "/" + scanFrontRounds)
                : ("pair-fail:" + lastReason + ":" + roundsOk + "/" + scanFrontRounds);
        StabilityResult stability = null;
        if (okPair && scanDeepEnabled) {
            String stabilitySni = scanFrontSni.isEmpty() ? scanSni : scanFrontSni;
            stability = stabilityProbe(ip, port, timeout, stabilitySni, scanMode, scanDeepRounds, scanDeepDelayMs);
            status = status + (stability.ok ? "+deep:" + stability.label : "+deep-fail:" + stability.reason);
        }
        double score = calculateScore(level, rtt, ttfb, downMbps, null, okPair, stability);
        return new ScanResult(ip, level, status, rtt, roundsOk, scanFrontRounds, downMbps, null, ttfb, okPair, lastStatusCode, score, stability == null ? null : stability.label, stability == null ? null : stability.avgRttMs, stability == null ? null : stability.jitterMs, stability == null ? null : stability.lossPct, stability == null ? 0 : stability.okChecks, stability == null ? 0 : stability.totalChecks, stability == null ? 0 : stability.sessionMs);
    }

    private double calculateScore(int level, Integer rtt, Integer ttfb, Double down, Double up, Boolean pairOk, StabilityResult stability) {
        double score = 0;
        if (level == 2) score += 5000;
        else if (level == 1) score += 1000;
        if (Boolean.TRUE.equals(pairOk)) score += 10000;
        if (Boolean.FALSE.equals(pairOk)) score -= 2500;
        if (down != null) score += down * 35.0;
        if (up != null) score += up * 5.0;
        if (ttfb != null) score -= ttfb * 1.2;
        if (rtt != null) score -= rtt * 0.35;
        if (stability != null) score += stability.score * 60.0;
        return score;
    }

    private Probe tcpProbe(String ip, int port, int timeoutMs) {
        long st = System.nanoTime();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), timeoutMs);
            int rtt = (int)((System.nanoTime() - st) / 1_000_000L);
            return new Probe(true, Math.max(1, rtt), "tcp-open");
        } catch (java.net.SocketTimeoutException e) {
            return new Probe(false, 0, "timeout");
        } catch (java.net.ConnectException e) {
            return new Probe(false, 0, "refused");
        } catch (Exception e) {
            return new Probe(false, 0, e.getClass().getSimpleName());
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private Probe tlsProbe(String ip, int port, int timeoutMs, String sniHost) {
        long st = System.nanoTime();
        Socket raw = new Socket();
        SSLSocket ssl = null;
        try {
            raw.connect(new InetSocketAddress(ip, port), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            String hostForSocket = sniHost.isEmpty() ? ip : sniHost;
            ssl = makeTlsSocket(raw, hostForSocket, port, timeoutMs);
            int rtt = (int)((System.nanoTime() - st) / 1_000_000L);
            return new Probe(true, Math.max(1, rtt), "tls-ok");
        } catch (java.net.SocketTimeoutException e) {
            return new Probe(false, 0, "timeout");
        } catch (javax.net.ssl.SSLException e) {
            return new Probe(false, 0, "ssl-fail");
        } catch (Exception e) {
            return new Probe(false, 0, e.getClass().getSimpleName());
        } finally {
            try { if (ssl != null) ssl.close(); else raw.close(); } catch (Exception ignored) {}
        }
    }

    private StabilityResult stabilityProbe(String ip, int port, int timeoutMs, String sniHost, int mode, int rounds, int delayMs) {
        long start = System.currentTimeMillis();
        ArrayList<Integer> rtts = new ArrayList<>();
        int ok = 0;
        String lastReason = "not-tested";
        for (int i = 0; i < rounds && !stopRequested.get(); i++) {
            Probe p;
            if (mode == 0) p = tcpProbe(ip, port, timeoutMs);
            else if (mode == 1) p = tlsProbe(ip, port, timeoutMs, sniHost == null ? "" : sniHost);
            else {
                Probe tcp = tcpProbe(ip, port, timeoutMs);
                if (!tcp.ok) p = tcp;
                else p = tlsProbe(ip, port, timeoutMs, sniHost == null ? "" : sniHost);
            }
            lastReason = p.reason;
            if (p.ok) {
                ok++;
                rtts.add(p.rttMs);
            }
            if (i < rounds - 1 && delayMs > 0 && !stopRequested.get()) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        int total = Math.max(1, rounds);
        long elapsed = Math.max(1L, System.currentTimeMillis() - start);
        if (ok <= 0 || rtts.isEmpty()) {
            return new StabilityResult(false, 0, total, null, null, 100.0, (int)elapsed, 0.0, "Low", lastReason);
        }
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int r : rtts) {
            sum += r;
            if (r < min) min = r;
            if (r > max) max = r;
        }
        int avg = Math.max(1, Math.round(sum / (float) rtts.size()));
        int jitter = Math.max(0, max - min);
        double lossPct = Math.max(0.0, ((total - ok) * 100.0) / total);
        double score = 100.0;
        score -= avg * 0.35;
        score -= jitter * 1.20;
        score -= lossPct * 1.60;
        score += ok * 12.0;
        score = Math.max(0.0, Math.min(100.0, score));
        String label = score >= 80 ? "High" : (score >= 60 ? "Medium" : "Low");
        return new StabilityResult(true, ok, total, avg, jitter, lossPct, (int)elapsed, score, label, ok == total ? "stable" : "partial");
    }



    private FrontingResult frontingHttpProbe(String ip, int port, int timeoutMs, String sniHost, String httpHost, String path, int maxBytes, int expectedStatus, String expectedMarker, List<String> verifyNames) {
        if (sniHost == null || sniHost.trim().isEmpty()) sniHost = httpHost;
        if (httpHost == null || httpHost.trim().isEmpty()) httpHost = sniHost;
        if (httpHost == null || httpHost.trim().isEmpty()) httpHost = ip;
        path = normalizePath(path);
        long start = System.nanoTime();
        long firstByteNs = 0L;
        Socket raw = new Socket();
        SSLSocket ssl = null;
        int bodyBytes = 0;
        int statusCode = 0;
        java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream(4096);
        java.io.ByteArrayOutputStream sample = new java.io.ByteArrayOutputStream(8192);
        boolean headerDone = false;
        try {
            raw.connect(new InetSocketAddress(ip, port), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            ssl = makeTlsSocket(raw, sniHost, port, timeoutMs, verifyNames);
            String req = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + httpHost + "\r\n" +
                    "User-Agent: Port443ScannerAndroid/1.3\r\n" +
                    "Accept: */*\r\n" +
                    "Range: bytes=0-" + Math.max(0, maxBytes - 1) + "\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            InputStream in = ssl.getInputStream();
            out.write(req.getBytes("US-ASCII"));
            out.flush();
            byte[] buf = new byte[16384];
            long readStart = 0L;
            long maxNs = (long)Math.max(timeoutMs * 3L, 7000L) * 1_000_000L;
            while (bodyBytes < maxBytes && System.nanoTime() - start < maxNs) {
                int n = in.read(buf);
                if (n < 0) break;
                if (firstByteNs == 0L) firstByteNs = System.nanoTime();
                int offset = 0;
                int count = n;
                if (!headerDone) {
                    for (int i = 0; i < n; i++) header.write(buf[i]);
                    byte[] h = header.toByteArray();
                    int idx = findHeaderEnd(h);
                    if (idx >= 0) {
                        headerDone = true;
                        String headerText = new String(h, 0, idx, "ISO-8859-1");
                        statusCode = parseStatusCode(headerText);
                        offset = Math.max(0, idx + 4 - (h.length - n));
                        if (offset > n) offset = n;
                        count = n - offset;
                        readStart = System.nanoTime();
                    } else if (h.length > 32768) {
                        return new FrontingResult(false, statusCode, 0, 0, 0, "bad-http-header", false);
                    } else {
                        continue;
                    }
                } else if (readStart == 0L) {
                    readStart = System.nanoTime();
                }
                if (count > 0) {
                    bodyBytes += count;
                    int canSample = Math.min(count, 8192 - sample.size());
                    if (canSample > 0) sample.write(buf, offset, canSample);
                }
            }
            if (!headerDone) return new FrontingResult(false, statusCode, 0, bodyBytes, 0, "no-http-header", false);
            boolean statusOk = expectedStatus > 0 ? statusCode == expectedStatus : (statusCode >= 200 && statusCode < 400);
            if (!statusOk) return new FrontingResult(false, statusCode, 0, bodyBytes, firstByteNs == 0L ? 0 : (int)((firstByteNs - start) / 1_000_000L), "http-" + statusCode, false);
            boolean markerOk = true;
            if (expectedMarker != null && !expectedMarker.trim().isEmpty()) {
                String bodyText = new String(sample.toByteArray(), "UTF-8");
                markerOk = bodyText.contains(expectedMarker.trim());
                if (!markerOk) return new FrontingResult(false, statusCode, 0, bodyBytes, firstByteNs == 0L ? 0 : (int)((firstByteNs - start) / 1_000_000L), "marker-mismatch", false);
            }
            if (bodyBytes <= 0) return new FrontingResult(false, statusCode, 0, 0, firstByteNs == 0L ? 0 : (int)((firstByteNs - start) / 1_000_000L), "no-body", markerOk);
            long end = System.nanoTime();
            double seconds = Math.max(0.001, (end - (readStart == 0L ? start : readStart)) / 1_000_000_000.0);
            double mbps = (bodyBytes * 8.0) / seconds / 1_000_000.0;
            int ttfb = firstByteNs == 0L ? 0 : (int)((firstByteNs - start) / 1_000_000L);
            return new FrontingResult(true, statusCode, mbps, bodyBytes, ttfb, "ok", markerOk);
        } catch (java.net.SocketTimeoutException e) {
            return new FrontingResult(false, statusCode, 0, bodyBytes, 0, "timeout", false);
        } catch (javax.net.ssl.SSLException e) {
            return new FrontingResult(false, statusCode, 0, bodyBytes, 0, "tls-fail", false);
        } catch (Exception e) {
            return new FrontingResult(false, statusCode, 0, bodyBytes, 0, e.getClass().getSimpleName(), false);
        } finally {
            try { if (ssl != null) ssl.close(); else raw.close(); } catch (Exception ignored) {}
        }
    }

    private int parseStatusCode(String headerText) {
        try {
            if (headerText == null) return 0;
            String[] lines = headerText.split("\\r?\\n");
            if (lines.length == 0) return 0;
            String[] parts = lines[0].trim().split("\\s+");
            if (parts.length >= 2) return Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        return 0;
    }

    private SpeedResult downloadSpeedProbe(String ip, int port, int timeoutMs, String host, String path, int maxBytes) {
        if (host == null || host.trim().isEmpty()) host = ip;
        path = normalizePath(path);
        long start = System.nanoTime();
        long firstByteNs = 0L;
        Socket raw = new Socket();
        SSLSocket ssl = null;
        int bodyBytes = 0;
        boolean headerDone = false;
        java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream(4096);
        try {
            raw.connect(new InetSocketAddress(ip, port), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            ssl = makeTlsSocket(raw, host, port, timeoutMs);
            String req = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "User-Agent: Port443ScannerAndroid/1.0\r\n" +
                    "Accept: */*\r\n" +
                    "Range: bytes=0-" + Math.max(0, maxBytes - 1) + "\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            InputStream in = ssl.getInputStream();
            out.write(req.getBytes("US-ASCII"));
            out.flush();
            byte[] buf = new byte[16384];
            long readStart = 0L;
            long maxNs = (long)Math.max(timeoutMs * 2L, 5000L) * 1_000_000L;
            while (bodyBytes < maxBytes && System.nanoTime() - start < maxNs) {
                int n = in.read(buf);
                if (n < 0) break;
                if (firstByteNs == 0L) firstByteNs = System.nanoTime();
                int offset = 0;
                int count = n;
                if (!headerDone) {
                    for (int i = 0; i < n; i++) header.write(buf[i]);
                    byte[] h = header.toByteArray();
                    int idx = findHeaderEnd(h);
                    if (idx >= 0) {
                        headerDone = true;
                        offset = Math.max(0, idx + 4 - (h.length - n));
                        if (offset > n) offset = n;
                        count = n - offset;
                        readStart = System.nanoTime();
                    } else if (h.length > 16384) {
                        return new SpeedResult(false, 0, 0, 0, "bad-http-header");
                    } else {
                        continue;
                    }
                } else if (readStart == 0L) {
                    readStart = System.nanoTime();
                }
                if (count > 0) bodyBytes += count;
            }
            long end = System.nanoTime();
            if (bodyBytes <= 0) return new SpeedResult(false, 0, 0, 0, "no-body");
            double seconds = Math.max(0.001, (end - (readStart == 0L ? start : readStart)) / 1_000_000_000.0);
            double mbps = (bodyBytes * 8.0) / seconds / 1_000_000.0;
            int ttfb = firstByteNs == 0L ? 0 : (int)((firstByteNs - start) / 1_000_000L);
            return new SpeedResult(true, mbps, bodyBytes, ttfb, "ok");
        } catch (java.net.SocketTimeoutException e) {
            return new SpeedResult(false, 0, bodyBytes, 0, "timeout");
        } catch (Exception e) {
            return new SpeedResult(false, 0, bodyBytes, 0, e.getClass().getSimpleName());
        } finally {
            try { if (ssl != null) ssl.close(); else raw.close(); } catch (Exception ignored) {}
        }
    }

    private SpeedResult uploadSpeedProbe(String ip, int port, int timeoutMs, String host, String path, int bytesToSend) {
        if (host == null || host.trim().isEmpty()) host = ip;
        path = normalizePath(path);
        Socket raw = new Socket();
        SSLSocket ssl = null;
        int sent = 0;
        long firstByteNs = 0L;
        long uploadStartNs = 0L;
        try {
            raw.connect(new InetSocketAddress(ip, port), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            ssl = makeTlsSocket(raw, host, port, timeoutMs);
            String req = "POST " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "User-Agent: EdgePulse/1.6\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: " + bytesToSend + "\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            InputStream in = ssl.getInputStream();
            out.write(req.getBytes("US-ASCII"));
            byte[] chunk = new byte[16384];
            for (int i = 0; i < chunk.length; i++) chunk[i] = (byte)(i * 31);
            uploadStartNs = System.nanoTime();
            while (sent < bytesToSend) {
                int n = Math.min(chunk.length, bytesToSend - sent);
                out.write(chunk, 0, n);
                sent += n;
            }
            out.flush();
            // Wait for an HTTP response. Measuring only write()/flush() often measures local
            // socket buffering, not real upload acceptance by the remote endpoint.
            java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int status = 0;
            while (header.size() < 32768) {
                int n = in.read(buf);
                if (n < 0) break;
                if (firstByteNs == 0L) firstByteNs = System.nanoTime();
                header.write(buf, 0, n);
                byte[] h = header.toByteArray();
                int idx = findHeaderEnd(h);
                if (idx >= 0) {
                    status = parseStatusCode(new String(h, 0, idx, "ISO-8859-1"));
                    break;
                }
            }
            long end = System.nanoTime();
            if (status < 200 || status >= 400) return new SpeedResult(false, 0, sent, 0, "http-" + status);
            double seconds = Math.max(0.001, (end - uploadStartNs) / 1_000_000_000.0);
            double mbps = (sent * 8.0) / seconds / 1_000_000.0;
            int ttfb = firstByteNs == 0L ? 0 : (int)((firstByteNs - uploadStartNs) / 1_000_000L);
            return new SpeedResult(true, mbps, sent, ttfb, "ok");
        } catch (java.net.SocketTimeoutException e) {
            return new SpeedResult(false, 0, sent, 0, "timeout");
        } catch (Exception e) {
            return new SpeedResult(false, 0, sent, 0, e.getClass().getSimpleName());
        } finally {
            try { if (ssl != null) ssl.close(); else raw.close(); } catch (Exception ignored) {}
        }
    }

    private SSLSocket makeTlsSocket(Socket raw, String host, int port, int timeoutMs) throws Exception {
        return makeTlsSocket(raw, host, port, timeoutMs, null);
    }

    private SSLSocket makeTlsSocket(Socket raw, String sniHost, int port, int timeoutMs, List<String> verifyNames) throws Exception {
        SSLSocketFactory factory = getSslFactory();
        String socketHost = (sniHost == null || sniHost.trim().isEmpty()) ? raw.getInetAddress().getHostAddress() : sniHost.trim();
        SSLSocket ssl = (SSLSocket) factory.createSocket(raw, socketHost, port, true);
        ssl.setSoTimeout(timeoutMs);
        ssl.setEnabledProtocols(ssl.getSupportedProtocols());
        String normalizedSni = normalizeHostname(socketHost);
        if (!normalizedSni.isEmpty()) {
            SSLParameters params = ssl.getSSLParameters();
            params.setServerNames(Collections.singletonList(new SNIHostName(normalizedSni)));
            ssl.setSSLParameters(params);
        }
        ssl.startHandshake();

        ArrayList<String> namesToVerify = new ArrayList<>();
        if (verifyNames != null) namesToVerify.addAll(verifyNames);
        else if (!normalizedSni.isEmpty()) namesToVerify.add(normalizedSni);
        if (!namesToVerify.isEmpty()) {
            boolean verified = false;
            for (String name : namesToVerify) {
                if (javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(name, ssl.getSession())) {
                    verified = true;
                    break;
                }
            }
            if (!verified) throw new javax.net.ssl.SSLHandshakeException("hostname-verification-failed");
        }
        return ssl;
    }

    private int findHeaderEnd(byte[] h) {
        for (int i = 0; i + 3 < h.length; i++) {
            if (h[i] == '\r' && h[i+1] == '\n' && h[i+2] == '\r' && h[i+3] == '\n') return i;
        }
        return -1;
    }

    private String normalizePath(String p) {
        if (p == null || p.trim().isEmpty()) return "/";
        p = p.trim();
        return p.startsWith("/") ? p : ("/" + p);
    }

    private SSLSocketFactory getSslFactory() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, null, new SecureRandom());
        return ctx.getSocketFactory();
    }

    private void updateUi(boolean force) {
        int total = currentIps.size();
        totalText.setText("کل: " + total);
        scannedText.setText("اسکن‌شده: " + scanned.get());
        openText.setText("باز: " + opened.get());
        maybeText.setText("نامطمئن: " + maybe.get());
        failText.setText("بسته/تایم‌اوت: " + failed.get());
        int prog = total == 0 ? 0 : (int)((scanned.get() * 1000L) / total);
        progressBar.setProgress(prog);
        copyBtn.setEnabled(opened.get() > 0);
        saveBtn.setEnabled(!results.isEmpty());
        if (shareBtn != null) shareBtn.setEnabled(opened.get() > 0);
        updateSpeedEta(total);
        updateAnalyticsSummary();
        if (force || results.size() % 5 == 0) refreshResultsText();
    }

    private int compareScanResults(ScanResult a, ScanResult b) {
        if (b.level != a.level) return b.level - a.level;
        int sc = Double.compare(b.score, a.score);
        if (sc != 0) return sc;
        double ad = a.downloadMbps == null ? -1.0 : a.downloadMbps;
        double bd = b.downloadMbps == null ? -1.0 : b.downloadMbps;
        int dcmp = Double.compare(bd, ad);
        if (dcmp != 0) return dcmp;
        double au = a.uploadMbps == null ? -1.0 : a.uploadMbps;
        double bu = b.uploadMbps == null ? -1.0 : b.uploadMbps;
        int ucmp = Double.compare(bu, au);
        if (ucmp != 0) return ucmp;
        int ar = a.rttMs == null ? 999999 : a.rttMs;
        int br = b.rttMs == null ? 999999 : b.rttMs;
        return Integer.compare(ar, br);
    }

    private void refreshResultsText() {
        List<ScanResult> copy;
        synchronized (results) { copy = new ArrayList<>(results); }
        Collections.sort(copy, this::compareScanResults);
        if (copy.isEmpty()) { resultText.setText("هنوز نتیجه‌ای ثبت نشده"); return; }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (ScanResult r : copy) {
            if (shown >= MAX_DISPLAY_ROWS) break;
            String mark = r.level == 2 ? "✅" : (r.level == 1 ? "⚠️" : "❌");
            sb.append(mark).append(' ').append(pad(r.ip, 15)).append("  ")
              .append(r.rttMs == null ? "-" : (r.rttMs + "ms"))
              .append("  DL:").append(r.downloadMbps == null ? "-" : String.format(Locale.US, "%.2fM", r.downloadMbps))
              .append("  UL:").append(r.uploadMbps == null ? "-" : String.format(Locale.US, "%.2fM", r.uploadMbps))
              .append("  HTTP:").append(r.httpStatus == null ? "-" : r.httpStatus)
              .append("  SCORE:").append(String.format(Locale.US, "%.0f", r.score))
              .append("  STB:").append(r.stabilityLabel == null ? "-" : r.stabilityLabel)
              .append("  AVG:").append(r.stabilityAvgRttMs == null ? "-" : (r.stabilityAvgRttMs + "ms"))
              .append("  J:").append(r.stabilityJitterMs == null ? "-" : r.stabilityJitterMs)
              .append("  L:").append(r.stabilityLossPct == null ? "-" : String.format(Locale.US, "%.0f%%", r.stabilityLossPct))
              .append("  ").append(r.okAttempts).append('/').append(r.totalAttempts)
              .append("  ").append(r.status).append('\n');
            shown++;
        }
        if (copy.size() > shown) sb.append("... ").append(copy.size() - shown).append(" مورد دیگر در CSV ذخیره می‌شود");
        resultText.setText(sb.toString());
    }


    private void updateAnalyticsSummary() {
        if (analyticsText == null) return;
        List<ScanResult> copy;
        synchronized (results) { copy = new ArrayList<>(results); }
        if (copy.isEmpty()) {
            analyticsText.setText("تحلیل: آماده");
            return;
        }
        int deepCount = 0;
        double scoreSum = 0;
        double lossSum = 0;
        int lossCount = 0;
        int jitterSum = 0;
        int jitterCount = 0;
        ScanResult best = null;
        for (ScanResult r : copy) {
            scoreSum += r.score;
            if (r.stabilityLabel != null && !r.stabilityLabel.isEmpty()) deepCount++;
            if (r.stabilityLossPct != null) { lossSum += r.stabilityLossPct; lossCount++; }
            if (r.stabilityJitterMs != null) { jitterSum += r.stabilityJitterMs; jitterCount++; }
            if (best == null || compareScanResults(r, best) < 0) best = r;
        }
        double avgScore = scoreSum / copy.size();
        double avgLoss = lossCount == 0 ? 0 : lossSum / lossCount;
        double avgJitter = jitterCount == 0 ? 0 : ((double) jitterSum / jitterCount);
        String bestText = best == null ? "-" : best.ip + " " + (best.stabilityLabel == null ? "" : best.stabilityLabel);
        analyticsText.setText(String.format(Locale.US, "تحلیل: %d نتیجه | Deep: %d | Avg Score: %.0f | Loss: %.1f%% | Jitter: %.1fms | Best: %s", copy.size(), deepCount, avgScore, avgLoss, avgJitter, bestText));
    }

    private void setControls(boolean enabled) {
        startBtn.setEnabled(enabled);
        prepareBtn.setEnabled(enabled);
        importBtn.setEnabled(enabled);
        generateBtn.setEnabled(enabled);
        sendGeneratedBtn.setEnabled(enabled);
        stopBtn.setEnabled(!enabled);
    }

    private void resetCounters() {
        results.clear();
        scanned.set(0); opened.set(0); failed.set(0); maybe.set(0); index.set(0); activeWorkers.set(0);
    }

    private void copyOpenIps() {
        List<ScanResult> open = getOpenResultsSorted();
        if (open.isEmpty()) { toast("IP باز وجود ندارد"); return; }
        StringBuilder sb = new StringBuilder();
        int limit = scanCopyTopN > 0 ? Math.min(scanCopyTopN, open.size()) : open.size();
        for (int i = 0; i < limit; i++) sb.append(open.get(i).ip).append('\n');
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("edgepulse_ips", sb.toString().trim()));
        toast((scanCopyTopN > 0 ? Math.min(scanCopyTopN, open.size()) : open.size()) + " IP کپی شد");
    }

    private void saveCsv() {
        if (results.isEmpty()) { toast("نتیجه‌ای برای ذخیره نیست"); return; }
        pendingCsv = buildCsv();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "port443_results.csv");
        startActivityForResult(i, REQ_SAVE_CSV);
    }

    private String buildCsv() {
        List<ScanResult> copy;
        synchronized (results) { copy = new ArrayList<>(results); }
        Collections.sort(copy, this::compareScanResults);
        StringBuilder sb = new StringBuilder("ip,result,status,rtt_ms,download_mbps,upload_mbps,ttfb_ms,http_status,fronting_ok,score,stability_label,stability_avg_rtt_ms,stability_jitter_ms,stability_loss_pct,stability_ok_checks,stability_total_checks,stability_session_ms,sni,host,path,ok_attempts,total_attempts\n");
        for (ScanResult r : copy) {
            sb.append(csvCell(r.ip)).append(',')
              .append(r.level == 2 ? "open" : (r.level == 1 ? "maybe" : "fail")).append(',')
              .append(csvCell(r.status)).append(',')
              .append(r.rttMs == null ? "" : r.rttMs).append(',')
              .append(r.downloadMbps == null ? "" : String.format(Locale.US, "%.3f", r.downloadMbps)).append(',')
              .append(r.uploadMbps == null ? "" : String.format(Locale.US, "%.3f", r.uploadMbps)).append(',')
              .append(r.ttfbMs == null ? "" : r.ttfbMs).append(',')
              .append(r.httpStatus == null ? "" : r.httpStatus).append(',')
              .append(r.frontingOk == null ? "" : r.frontingOk).append(',')
              .append(String.format(Locale.US, "%.2f", r.score)).append(',')
              .append(csvCell(r.stabilityLabel)).append(',')
              .append(r.stabilityAvgRttMs == null ? "" : r.stabilityAvgRttMs).append(',')
              .append(r.stabilityJitterMs == null ? "" : r.stabilityJitterMs).append(',')
              .append(r.stabilityLossPct == null ? "" : String.format(Locale.US, "%.2f", r.stabilityLossPct)).append(',')
              .append(r.stabilityOkChecks).append(',')
              .append(r.stabilityTotalChecks).append(',')
              .append(r.stabilitySessionMs).append(',')
              .append(csvCell(scanFrontingEnabled ? scanFrontSni : "")).append(',')
              .append(csvCell(scanFrontingEnabled ? scanFrontHost : "")).append(',')
              .append(csvCell(scanFrontingEnabled ? scanFrontPath : "")).append(',')
              .append(r.okAttempts).append(',')
              .append(r.totalAttempts).append('\n');
        }
        return sb.toString();
    }

    private List<ScanResult> getOpenResultsSorted() {
        List<ScanResult> open = new ArrayList<>();
        synchronized (results) { for (ScanResult r : results) if (r.level == 2) open.add(r); }
        Collections.sort(open, this::compareScanResults);
        return open;
    }


    private void shareOpenIps() {
        List<ScanResult> open = getOpenResultsSorted();
        if (open.isEmpty()) { toast("IP باز وجود ندارد"); return; }
        StringBuilder sb = new StringBuilder();
        for (ScanResult r : open) {
            if (Boolean.TRUE.equals(r.frontingOk) || scanFrontingEnabled) {
                sb.append(r.ip)
                  .append(" | sni=").append(scanFrontSni)
                  .append(" | host=").append(scanFrontHost)
                  .append(" | path=").append(scanFrontPath)
                  .append(" | score=").append(String.format(Locale.US, "%.0f", r.score))
                  .append(" | dl=").append(r.downloadMbps == null ? "" : String.format(Locale.US, "%.2fMbps", r.downloadMbps))
                  .append('\n');
            } else {
                sb.append(r.ip).append('\n');
            }
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString().trim());
        startActivity(Intent.createChooser(send, "اشتراک IPهای باز"));
    }

    private void updateSpeedEta(int total) {
        if (speedText == null || etaText == null) return;
        int done = scanned.get();
        if (!scanning.get() || scanStartMs == 0L || done == 0) {
            speedText.setText("سرعت: 0 IP/s");
            etaText.setText("زمان باقی‌مانده: -");
            return;
        }
        double elapsed = Math.max(0.001, (System.currentTimeMillis() - scanStartMs) / 1000.0);
        double speed = done / elapsed;
        int remain = Math.max(0, total - done);
        long eta = speed <= 0 ? 0 : Math.round(remain / speed);
        speedText.setText(String.format(Locale.US, "سرعت: %.1f IP/s", speed));
        etaText.setText("زمان باقی‌مانده: " + formatDuration(eta));
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "-";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private void saveSettings() {
        if (portInput == null) return;
        SharedPreferences.Editor e = getSharedPreferences("settings", MODE_PRIVATE).edit();
        e.putString("port", portInput.getText().toString());
        e.putString("timeout", timeoutInput.getText().toString());
        e.putString("concurrency", concurrencyInput.getText().toString());
        e.putString("attempts", attemptsInput.getText().toString());
        e.putString("minOk", minOkInput.getText().toString());
        e.putString("sni", sniInput.getText().toString());
        e.putBoolean("skipPrivate", skipPrivateBox != null && skipPrivateBox.isChecked());
        e.putInt("mode", modeSpinner == null ? 0 : modeSpinner.getSelectedItemPosition());
        e.putBoolean("speedEnabled", speedTestBox != null && speedTestBox.isChecked());
        e.putBoolean("uploadEnabled", uploadTestBox != null && uploadTestBox.isChecked());
        e.putString("speedHost", speedHostInput == null ? "" : speedHostInput.getText().toString());
        e.putString("downloadPath", downloadPathInput == null ? "/" : downloadPathInput.getText().toString());
        e.putString("downloadKb", downloadKbInput == null ? "1024" : downloadKbInput.getText().toString());
        e.putString("uploadPath", uploadPathInput == null ? "/" : uploadPathInput.getText().toString());
        e.putString("uploadKb", uploadKbInput == null ? "512" : uploadKbInput.getText().toString());
        e.putBoolean("frontingEnabled", frontingModeBox != null && frontingModeBox.isChecked());
        e.putBoolean("deepAnalysis", deepAnalysisBox != null && deepAnalysisBox.isChecked());
        e.putString("deepRounds", deepRoundsInput == null ? "5" : deepRoundsInput.getText().toString());
        e.putString("deepDelay", deepDelayInput == null ? "200" : deepDelayInput.getText().toString());
        e.putString("frontSni", frontSniInput == null ? "" : frontSniInput.getText().toString());
        e.putString("frontHost", frontHostInput == null ? "" : frontHostInput.getText().toString());
        e.putString("frontPath", frontPathInput == null ? "/" : frontPathInput.getText().toString());
        e.putString("expectedStatus", expectedStatusInput == null ? "0" : expectedStatusInput.getText().toString());
        e.putString("expectedMarker", expectedMarkerInput == null ? "" : expectedMarkerInput.getText().toString());
        e.putString("frontKb", frontKbInput == null ? "512" : frontKbInput.getText().toString());
        e.putString("frontRounds", frontRoundsInput == null ? "1" : frontRoundsInput.getText().toString());
        e.putString("verifyNames", verifyNamesInput == null ? "" : verifyNamesInput.getText().toString());
        e.putString("copyTop", copyTopInput == null ? "0" : copyTopInput.getText().toString());
        e.putBoolean("advancedMode", advancedModeBox != null && advancedModeBox.isChecked());
        e.apply();
    }

    private void loadSettings() {
        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        if (portInput == null) return;
        portInput.setText(p.getString("port", "443"));
        timeoutInput.setText(p.getString("timeout", "3000"));
        concurrencyInput.setText(p.getString("concurrency", "80"));
        attemptsInput.setText(p.getString("attempts", "2"));
        minOkInput.setText(p.getString("minOk", "1"));
        sniInput.setText(p.getString("sni", ""));
        if (skipPrivateBox != null) skipPrivateBox.setChecked(p.getBoolean("skipPrivate", true));
        if (modeSpinner != null) modeSpinner.setSelection(p.getInt("mode", 0));
        if (speedTestBox != null) speedTestBox.setChecked(p.getBoolean("speedEnabled", false));
        if (uploadTestBox != null) uploadTestBox.setChecked(p.getBoolean("uploadEnabled", false));
        if (speedHostInput != null) speedHostInput.setText(p.getString("speedHost", ""));
        if (downloadPathInput != null) downloadPathInput.setText(p.getString("downloadPath", "/"));
        if (downloadKbInput != null) downloadKbInput.setText(p.getString("downloadKb", "1024"));
        if (uploadPathInput != null) uploadPathInput.setText(p.getString("uploadPath", "/"));
        if (uploadKbInput != null) uploadKbInput.setText(p.getString("uploadKb", "512"));
        if (frontingModeBox != null) frontingModeBox.setChecked(p.getBoolean("frontingEnabled", false));
        if (deepAnalysisBox != null) deepAnalysisBox.setChecked(p.getBoolean("deepAnalysis", false));
        if (deepRoundsInput != null) deepRoundsInput.setText(p.getString("deepRounds", "5"));
        if (deepDelayInput != null) deepDelayInput.setText(p.getString("deepDelay", "200"));
        if (frontSniInput != null) frontSniInput.setText(p.getString("frontSni", ""));
        if (frontHostInput != null) frontHostInput.setText(p.getString("frontHost", ""));
        if (frontPathInput != null) frontPathInput.setText(p.getString("frontPath", "/"));
        if (expectedStatusInput != null) expectedStatusInput.setText(p.getString("expectedStatus", "0"));
        if (expectedMarkerInput != null) expectedMarkerInput.setText(p.getString("expectedMarker", ""));
        if (frontKbInput != null) frontKbInput.setText(p.getString("frontKb", "512"));
        if (frontRoundsInput != null) frontRoundsInput.setText(p.getString("frontRounds", "1"));
        if (verifyNamesInput != null) verifyNamesInput.setText(p.getString("verifyNames", ""));
        if (copyTopInput != null) copyTopInput.setText(p.getString("copyTop", "0"));
        if (advancedModeBox != null) advancedModeBox.setChecked(p.getBoolean("advancedMode", false));
        applyModeVisibility();
    }

    private void applyModeVisibility() {
        boolean advanced = advancedModeBox != null && advancedModeBox.isChecked();
        for (View v : advancedViews) if (v != null) v.setVisibility(advanced ? View.VISIBLE : View.GONE);
        // Note: leaving Pair/Fronting mode untouched. Forcing it on whenever Advanced was
        // disabled surprised users who just wanted a plain TCP scan.
        if (!advanced) {
            // Don't overwrite the port the user explicitly chose. Only seed empties.
            if (portInput != null && portInput.getText().toString().trim().isEmpty()) portInput.setText("443");
            if (timeoutInput != null && timeoutInput.getText().toString().trim().isEmpty()) timeoutInput.setText("3000");
            if (concurrencyInput != null && concurrencyInput.getText().toString().trim().isEmpty()) concurrencyInput.setText("80");
            if (attemptsInput != null && attemptsInput.getText().toString().trim().isEmpty()) attemptsInput.setText("2");
            if (minOkInput != null && minOkInput.getText().toString().trim().isEmpty()) minOkInput.setText("1");
        }
    }


    private List<String> parseVerifyNames(String raw, String defaultSni, String defaultHost) {
        ArrayList<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String x : raw.split("[\\s,;]+")) {
                String h = normalizeHostname(x);
                if (!h.isEmpty() && seen.add(h)) out.add(h);
            }
        }
        if (out.isEmpty()) {
            String sni = normalizeHostname(defaultSni);
            String host = normalizeHostname(defaultHost);
            if (!sni.isEmpty() && seen.add(sni)) out.add(sni);
            if (!host.isEmpty() && seen.add(host)) out.add(host);
        }
        return out;
    }

    private String normalizeHostname(String value) {
        if (value == null) return "";
        String h = value.trim().toLowerCase(Locale.US);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty() || isIp(h) || h.length() > 253) return "";
        String[] labels = h.split("\\.", -1);
        if (labels.length < 2) return "";
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return "";
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '-') return "";
            }
        }
        return h;
    }

    private List<String> filterPublicIps(List<String> ips) {
        ArrayList<String> out = new ArrayList<>();
        for (String ip : ips) if (!isPrivateOrReserved(ip)) out.add(ip);
        return out;
    }

    private boolean isPrivateOrReserved(String ip) {
        long x = ipToLong(ip);
        long a = (x >> 24) & 255;
        long b = (x >> 16) & 255;
        if (a == 10 || a == 127 || a == 0) return true;
        if (a == 169 && b == 254) return true;
        if (a == 172 && b >= 16 && b <= 31) return true;
        if (a == 192 && b == 168) return true;
        if (a == 100 && b >= 64 && b <= 127) return true;
        if (a >= 224) return true;
        if (a == 192 && b == 0) return true;
        if (a == 192 && b == 2) return true;
        if (a == 198 && (b == 18 || b == 19 || b == 51)) return true;
        if (a == 203 && b == 0) return true;
        return false;
    }

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, REQ_OPEN_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_OPEN_FILE) {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                ipInput.setText(sb.toString());
                prepareList(true);
            } catch (Exception e) { toast("خطا در خواندن فایل"); }
        } else if (req == REQ_SAVE_CSV) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(pendingCsv.getBytes("UTF-8"));
                toast("CSV ذخیره شد");
            } catch (Exception e) { toast("خطا در ذخیره فایل"); }
        }
    }

    private void generateRanges() {
        List<String> parsed = parseInput(rangeInput.getText().toString(), true);
        generatedIps = parsed;
        rangeCountText.setText("تولید شده: " + parsed.size());
        toast(parsed.size() + " IP تولید شد");
    }

    private List<String> parseInput(String text, boolean capLarge) {
        ArrayList<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Split on whitespace/commas/semicolons but NOT '-' or '/' since those are part of tokens.
        String[] tokens = text.split("[\\s,;]+");
        for (String raw : tokens) {
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            List<String> part = new ArrayList<>();
            // CIDR like 1.2.3.0/24
            if (t.contains("/")) part = expandCidr(t, capLarge);
            // Range like 1.2.3.4-1.2.3.20 (only when there's NO slash). The first '-' separates the two IPs;
            // each IP itself has dots only, never '-', so a single split is sufficient.
            else if (t.contains("-")) {
                int dash = t.indexOf('-');
                String a = t.substring(0, dash).trim();
                String b = t.substring(dash + 1).trim();
                part = expandRange(a, b, capLarge);
            } else if (isIp(t)) {
                part.add(t);
            }
            for (String ip : part) if (seen.add(ip)) out.add(ip);
        }
        Collections.sort(out, (a, b) -> Long.compare(ipToLong(a), ipToLong(b)));
        return out;
    }

    private List<String> expandCidr(String cidr, boolean capLarge) {
        ArrayList<String> out = new ArrayList<>();
        try {
            String[] p = cidr.split("/", 2);
            if (p.length != 2 || !isIp(p[0])) return out;
            int mask = Integer.parseInt(p[1].trim());
            if (mask < 0 || mask > 32) return out;
            long base = ipToLong(p[0]);
            long hosts = mask == 0 ? (1L << 32) : (1L << (32 - mask));
            if (capLarge && hosts > 300000) { toast("رنج خیلی بزرگ است: " + cidr); return out; }
            long maskLong = mask == 0 ? 0L : (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;
            long net = base & maskLong;
            long start = hosts <= 2 ? 0 : 1;
            long end = hosts <= 2 ? hosts : hosts - 1;
            for (long i = start; i < end; i++) out.add(longToIp((net + i) & 0xFFFFFFFFL));
        } catch (Exception ignored) {}
        return out;
    }

    private List<String> expandRange(String a, String b, boolean capLarge) {
        ArrayList<String> out = new ArrayList<>();
        if (!isIp(a) || !isIp(b)) return out;
        long s = ipToLong(a), e = ipToLong(b);
        if (e < s) return out;
        if (capLarge && e - s + 1 > 300000) { toast("بازه خیلی بزرگ است"); return out; }
        for (long x = s; x <= e; x++) out.add(longToIp(x));
        return out;
    }

    private boolean isIp(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) return false;
        try {
            for (String s : p) {
                if (s.isEmpty() || s.length() > 3) return false;
                int n = Integer.parseInt(s);
                if (n < 0 || n > 255) return false;
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private long ipToLong(String ip) {
        String[] p = ip.split("\\.");
        long r = 0;
        for (String s : p) r = ((r << 8) + Integer.parseInt(s)) & 0xFFFFFFFFL;
        return r;
    }

    private String longToIp(long n) {
        return ((n >> 24) & 255) + "." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }

    private int median(List<Integer> xs) {
        Collections.sort(xs);
        return xs.get(xs.size() / 2);
    }

    private int toInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private String joinLines(List<String> xs) { StringBuilder sb = new StringBuilder(); for (String x : xs) sb.append(x).append('\n'); return sb.toString().trim(); }
    private String pad(String s, int len) { return String.format(Locale.US, "%-" + len + "s", s); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    /** RFC 4180-ish CSV escaping: wraps the cell in double-quotes if it contains comma, quote, CR, or LF, and doubles internal quotes. */
    private String csvCell(String s) {
        if (s == null) return "";
        if (s.isEmpty()) return "";
        boolean needsQuoting = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') { needsQuoting = true; break; }
        }
        if (!needsQuoting) return s;
        StringBuilder out = new StringBuilder(s.length() + 4);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') out.append('"');
            out.append(c);
        }
        out.append('"');
        return out.toString();
    }


    private void styleSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.rgb(7, 11, 19));
            getWindow().setNavigationBarColor(Color.rgb(7, 11, 19));
        }
    }

    private LinearLayout heroCard(LinearLayout parent) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(makeGradientBg(Color.rgb(55, 48, 163), Color.rgb(15, 118, 110), dp(28), Color.argb(80, 255, 255, 255)));
        if (android.os.Build.VERSION.SDK_INT >= 21) l.setElevation(dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        parent.addView(l, lp);
        return l;
    }

    private TextView chip(String text) {
        TextView c = tv(text, 11, Color.rgb(241, 245, 249), true);
        c.setPadding(dp(10), dp(5), dp(10), dp(5));
        c.setGravity(Gravity.CENTER);
        c.setBackground(makeBg(Color.argb(55, 255, 255, 255), dp(999), Color.argb(65, 255, 255, 255)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(12), dp(4), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(makeGradientBg(Color.rgb(30, 41, 59), Color.rgb(17, 24, 39), dp(22), Color.rgb(51, 65, 85)));
        if (android.os.Build.VERSION.SDK_INT >= 21) l.setElevation(dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        parent.addView(l, lp);
        return l;
    }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private LinearLayout field(String label, EditText input) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(4), 0, dp(4), 0); TextView lab = small(label); lab.setTextColor(Color.rgb(203,213,225)); l.addView(lab); l.addView(input); return l; }
    private TextView label(String s) { TextView t = tv(s, 15, Color.rgb(226,232,240), true); t.setPadding(0, 0, 0, dp(10)); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); return t; }
    private TextView small(String s) { TextView t = tv(s, 12, Color.rgb(148,163,184), false); t.setPadding(0, dp(4), 0, dp(6)); return t; }
    private TextView stat(String s) { TextView t = tv(s, 14, TEXT, true); t.setPadding(dp(12), dp(9), dp(12), dp(9)); t.setBackground(makeBg(Color.argb(70, 15, 23, 42), dp(14), Color.rgb(51,65,85))); return t; }
    private TextView tv(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(true); t.setLineSpacing(0, 1.05f); if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); return t; }
    private EditText editNumber(String s) { EditText e = baseEdit(); e.setText(s); e.setInputType(InputType.TYPE_CLASS_NUMBER); return e; }
    private EditText editOneLine(String hint) { EditText e = baseEdit(); e.setSingleLine(true); e.setHint(hint); return e; }
    private EditText editMultiline(String hint, int minLines) { EditText e = baseEdit(); e.setHint(hint); e.setMinLines(minLines); e.setGravity(Gravity.TOP | Gravity.START); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); e.setTextDirection(View.TEXT_DIRECTION_LTR); e.setTypeface(Typeface.MONOSPACE); return e; }
    private EditText baseEdit() { EditText e = new EditText(this); e.setTextColor(TEXT); e.setHintTextColor(Color.rgb(100,116,139)); e.setTextSize(13); e.setPadding(dp(12), dp(10), dp(12), dp(10)); e.setMinHeight(dp(48)); e.setBackground(makeBg(Color.rgb(15,23,42), dp(16), Color.rgb(51,65,85))); return e; }
    private Button btn(String s, int color) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setPadding(dp(10), 0, dp(10), 0); b.setBackground(makeButtonBg(color)); if (android.os.Build.VERSION.SDK_INT >= 21) b.setElevation(dp(3)); return b; }
    private LinearLayout.LayoutParams matchLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(5), 0, dp(9)); return lp; }
    private LinearLayout.LayoutParams weightLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1); lp.setMargins(dp(4), dp(5), dp(4), dp(9)); return lp; }
    private android.graphics.drawable.GradientDrawable makeBg(int color, int radius, int stroke) { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (stroke != 0) g.setStroke(dp(1), stroke); return g; }
    private android.graphics.drawable.GradientDrawable makeGradientBg(int start, int end, int radius, int stroke) { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{start, end}); g.setCornerRadius(radius); if (stroke != 0) g.setStroke(dp(1), stroke); return g; }
    private android.graphics.drawable.Drawable makeButtonBg(int color) { android.graphics.drawable.GradientDrawable normal = makeGradientBg(lighten(color, 18), color, dp(24), 0); if (android.os.Build.VERSION.SDK_INT >= 21) return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(70,255,255,255)), normal, null); return normal; }
    private int lighten(int color, int amount) { return Color.rgb(Math.min(255, Color.red(color)+amount), Math.min(255, Color.green(color)+amount), Math.min(255, Color.blue(color)+amount)); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    static class Probe {
        final boolean ok; final int rttMs; final String reason;
        Probe(boolean ok, int rttMs, String reason) { this.ok = ok; this.rttMs = rttMs; this.reason = reason; }
    }
    static class StabilityResult {
        final boolean ok; final int okChecks; final int totalChecks; final Integer avgRttMs; final Integer jitterMs; final Double lossPct; final int sessionMs; final double score; final String label; final String reason;
        StabilityResult(boolean ok, int okChecks, int totalChecks, Integer avgRttMs, Integer jitterMs, Double lossPct, int sessionMs, double score, String label, String reason) {
            this.ok = ok; this.okChecks = okChecks; this.totalChecks = totalChecks; this.avgRttMs = avgRttMs; this.jitterMs = jitterMs; this.lossPct = lossPct; this.sessionMs = sessionMs; this.score = score; this.label = label; this.reason = reason;
        }
    }
    static class SpeedResult {
        final boolean ok; final double mbps; final int bytes; final Integer ttfbMs; final String reason;
        SpeedResult(boolean ok, double mbps, int bytes, Integer ttfbMs, String reason) {
            this.ok = ok; this.mbps = mbps; this.bytes = bytes; this.ttfbMs = ttfbMs; this.reason = reason;
        }
    }
    static class FrontingResult {
        final boolean ok; final Integer statusCode; final double mbps; final int bytes; final Integer ttfbMs; final String reason; final boolean markerOk;
        FrontingResult(boolean ok, Integer statusCode, double mbps, int bytes, Integer ttfbMs, String reason, boolean markerOk) {
            this.ok = ok; this.statusCode = statusCode; this.mbps = mbps; this.bytes = bytes; this.ttfbMs = ttfbMs; this.reason = reason; this.markerOk = markerOk;
        }
    }
    static class ScanResult {
        final String ip; final int level; final String status; final Integer rttMs; final int okAttempts; final int totalAttempts;
        final Double downloadMbps; final Double uploadMbps; final Integer ttfbMs; final Boolean frontingOk; final Integer httpStatus; final double score;
        final String stabilityLabel; final Integer stabilityAvgRttMs; final Integer stabilityJitterMs; final Double stabilityLossPct; final int stabilityOkChecks; final int stabilityTotalChecks; final int stabilitySessionMs;
        ScanResult(String ip, int level, String status, Integer rttMs, int okAttempts, int totalAttempts, Double downloadMbps, Double uploadMbps, Integer ttfbMs, Boolean frontingOk, Integer httpStatus, double score,
                   String stabilityLabel, Integer stabilityAvgRttMs, Integer stabilityJitterMs, Double stabilityLossPct, int stabilityOkChecks, int stabilityTotalChecks, int stabilitySessionMs) {
            this.ip = ip; this.level = level; this.status = status; this.rttMs = rttMs; this.okAttempts = okAttempts; this.totalAttempts = totalAttempts;
            this.downloadMbps = downloadMbps; this.uploadMbps = uploadMbps; this.ttfbMs = ttfbMs; this.frontingOk = frontingOk; this.httpStatus = httpStatus; this.score = score;
            this.stabilityLabel = stabilityLabel; this.stabilityAvgRttMs = stabilityAvgRttMs; this.stabilityJitterMs = stabilityJitterMs; this.stabilityLossPct = stabilityLossPct;
            this.stabilityOkChecks = stabilityOkChecks; this.stabilityTotalChecks = stabilityTotalChecks; this.stabilitySessionMs = stabilitySessionMs;
        }
    }
}
