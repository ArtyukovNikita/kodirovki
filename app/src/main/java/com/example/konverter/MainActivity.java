package com.example.konverter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText editInput, editOutput;
    private RadioGroup radioGroupSource, radioGroupTarget;
    private RadioButton radioSourceAuto;
    private Button btnOpenFile, btnConvert, btnSaveFile, btnClear;

    private static final String[] CHARSETS = {"UTF-8", "windows-1251", "KOI8-R"};
    private String detectedCharset = "UTF-8";
    private static final String FOLDER_NAME = "konverter";

    // ActivityResultLauncher для выбора файла
    private final ActivityResultLauncher<String[]> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    openFile(uri);
                }
            });

    // Launcher для запроса разрешений (только для Android 6-10)
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    openFileLauncher.launch(new String[]{"*/*"});
                } else {
                    Toast.makeText(this, "Необходимо разрешение на чтение файлов", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        // Создаем папку konverter в папке приложения
        createKonverterFolder();
    }

    private void initViews() {
        editInput = findViewById(R.id.editInput);
        editOutput = findViewById(R.id.editOutput);
        radioGroupSource = findViewById(R.id.radioGroupSource);
        radioGroupTarget = findViewById(R.id.radioGroupTarget);
        radioSourceAuto = findViewById(R.id.radioSourceAuto);
        btnOpenFile = findViewById(R.id.btnOpenFile);
        btnConvert = findViewById(R.id.btnConvert);
        btnSaveFile = findViewById(R.id.btnSaveFile);
        btnClear = findViewById(R.id.btnClear);
    }

    private void setupListeners() {
        btnOpenFile.setOnClickListener(v -> openFileWithPermissions());
        btnConvert.setOnClickListener(v -> convertEncoding());
        btnSaveFile.setOnClickListener(v -> saveToFile());
        btnClear.setOnClickListener(v -> clearAll());
    }

    private void openFileWithPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ не требует разрешений для OpenDocument
            openFileLauncher.launch(new String[]{"*/*"});
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-12: проверяем разрешение на чтение
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE
                });
                return;
            } else {
                openFileLauncher.launch(new String[]{"*/*"});
            }
        } else {
            // Android 5 и ниже
            openFileLauncher.launch(new String[]{"*/*"});
        }
    }

    private void openFile(Uri uri) {
        try {
            // Определяем кодировку файла
            String charset = detectCharset(uri);
            detectedCharset = charset;

            // Читаем файл
            String content = readFileFromUri(uri, charset);
            editInput.setText(content);

            // Если выбран режим "Авто", показываем определенную кодировку
            if (radioSourceAuto.isChecked()) {
                Toast.makeText(this, "Файл открыт. Определена кодировка: " + detectedCharset, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Файл открыт", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при чтении файла: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String detectCharset(Uri uri) throws IOException {
        // Пробуем разные кодировки и выбираем ту, которая дает наибольшее количество русских букв
        String[] charsetsToTry = {"UTF-8", "windows-1251", "KOI8-R"};
        String bestCharset = "UTF-8";
        int maxRussianLetters = 0;

        for (String charset : charsetsToTry) {
            try {
                String content = readFileFromUri(uri, charset);
                int russianLetterCount = countRussianLetters(content);

                if (russianLetterCount > maxRussianLetters) {
                    maxRussianLetters = russianLetterCount;
                    bestCharset = charset;
                }
            } catch (Exception e) {
                // Игнорируем ошибки для неподходящих кодировок
            }
        }

        return bestCharset;
    }

    private int countRussianLetters(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if ((c >= 'А' && c <= 'я') || c == 'ё' || c == 'Ё') {
                count++;
            }
        }
        return count;
    }

    private String readFileFromUri(Uri uri, String charsetName) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        Charset charset = Charset.forName(charsetName);

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }

        return stringBuilder.toString();
    }

    private void convertEncoding() {
        String inputText = editInput.getText().toString();

        if (TextUtils.isEmpty(inputText)) {
            Toast.makeText(this, "Введите текст для конвертации", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Определяем исходную кодировку
            String sourceCharset;
            int sourceId = radioGroupSource.getCheckedRadioButtonId();

            if (sourceId == R.id.radioSourceAuto) {
                sourceCharset = detectedCharset;
            } else if (sourceId == R.id.radioSourceUTF8) {
                sourceCharset = "UTF-8";
            } else if (sourceId == R.id.radioSourceWindows1251) {
                sourceCharset = "windows-1251";
            } else if (sourceId == R.id.radioSourceKOI8) {
                sourceCharset = "KOI8-R";
            } else {
                sourceCharset = "UTF-8";
            }

            // Определяем целевую кодировку
            String targetCharset = getTargetCharset();

            // Конвертируем
            String convertedText = convertText(inputText, sourceCharset, targetCharset);
            editOutput.setText(convertedText);

            Toast.makeText(this, "Конвертация выполнена: " + sourceCharset + " → " + targetCharset, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при конвертации: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String convertText(String text, String fromCharset, String toCharset) {
        try {
            // Конвертируем строку из одной кодировки в другую
            byte[] bytes = text.getBytes(Charset.forName(fromCharset));
            return new String(bytes, Charset.forName(toCharset));
        } catch (Exception e) {
            e.printStackTrace();
            return text;
        }
    }

    private String getTargetCharset() {
        int targetId = radioGroupTarget.getCheckedRadioButtonId();
        if (targetId == R.id.radioTargetUTF8) {
            return "UTF-8";
        } else if (targetId == R.id.radioTargetWindows1251) {
            return "windows-1251";
        } else if (targetId == R.id.radioTargetKOI8) {
            return "KOI8-R";
        } else {
            return "UTF-8";
        }
    }

    private void saveToFile() {
        String outputText = editOutput.getText().toString();

        if (TextUtils.isEmpty(outputText)) {
            Toast.makeText(this, "Нет текста для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        // Сохраняем файл в папку konverter (не требует разрешений)
        saveFileToAppFolder(outputText);
    }

    private void createKonverterFolder() {
        try {
            // Получаем папку приложения (не требует разрешений)
            File appFolder = getExternalFilesDir(null);
            if (appFolder != null) {
                File konverterFolder = new File(appFolder, FOLDER_NAME);
                if (!konverterFolder.exists()) {
                    boolean created = konverterFolder.mkdirs();
                    if (created) {
                        // Показываем путь при первом создании
                        Toast.makeText(this, "Папка создана:\n" + konverterFolder.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveFileToAppFolder(String text) {
        try {
            // Определяем целевую кодировку
            String targetCharset = getTargetCharset();

            // Получаем папку приложения
            File appFolder = getExternalFilesDir(null);
            if (appFolder == null) {
                Toast.makeText(this, "Ошибка доступа к хранилищу", Toast.LENGTH_SHORT).show();
                return;
            }

            // Создаем папку konverter (если еще не создана)
            File konverterFolder = new File(appFolder, FOLDER_NAME);
            if (!konverterFolder.exists()) {
                boolean created = konverterFolder.mkdirs();
                if (!created) {
                    Toast.makeText(this, "Не удалось создать папку", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Генерируем имя файла с датой и временем
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "konverter_" + sdf.format(new Date()) + ".txt";

            File outputFile = new File(konverterFolder, fileName);

            // Сохраняем файл
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, Charset.forName(targetCharset))) {
                writer.write(text);
            }

            // Показываем путь к файлу
            String filePath = outputFile.getAbsolutePath();
            Toast.makeText(this, "Файл сохранен:\n" + filePath, Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при сохранении файла: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAll() {
        editInput.setText("");
        editOutput.setText("");
        radioSourceAuto.setChecked(true);
        detectedCharset = "UTF-8";
        Toast.makeText(this, "Поля очищены", Toast.LENGTH_SHORT).show();
    }
}