package com.subtitle.editor;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    MediaPlayer mediaPlayer;
    EditText etLyrics;
    Button btnMark, btnPlayPause, btnSelect, btnSave, btnFwd, btnRew;
    TextView tvTime;
    SeekBar sbProgress; 
    String songName = "LetraSincronizada";
    boolean isUserSeeking = false; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        etLyrics = (EditText) findViewById(R.id.etLyrics);
        btnMark = (Button) findViewById(R.id.btnMark);
        btnPlayPause = (Button) findViewById(R.id.btnPlayPause);
        btnSelect = (Button) findViewById(R.id.btnSelectAudio);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnFwd = (Button) findViewById(R.id.btnFwd);
        btnRew = (Button) findViewById(R.id.btnRew);
        tvTime = (TextView) findViewById(R.id.tvCurrentTime);
        sbProgress = (SeekBar) findViewById(R.id.sbProgress); 

        // Forzamos al EditText a aceptar y mantener múltiples líneas en tiempo de ejecución
        etLyrics.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etLyrics.setSingleLine(false);

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                startActivityForResult(i, 1);
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mediaPlayer == null) return;
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause(); btnPlayPause.setText("Play");
                } else {
                    mediaPlayer.start(); btnPlayPause.setText("Pause"); updateTimer();
                }
            }
        });

        btnRew.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mediaPlayer != null) {
                    int newPos = mediaPlayer.getCurrentPosition() - 5000;
                    mediaPlayer.seekTo(Math.max(newPos, 0));
                    if (!isUserSeeking) sbProgress.setProgress(mediaPlayer.getCurrentPosition());
                }
            }
        });

        btnFwd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mediaPlayer != null) {
                    int newPos = mediaPlayer.getCurrentPosition() + 5000;
                    mediaPlayer.seekTo(Math.min(newPos, mediaPlayer.getDuration()));
                    if (!isUserSeeking) sbProgress.setProgress(mediaPlayer.getCurrentPosition());
                }
            }
        });

        btnMark.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { handleMarking(); }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveFile(); }
        });

        // Configuración de interacción del usuario con la barra de progreso
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    tvTime.setText(formatTime(progress));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { isUserSeeking = false; }
        });
    }

    private void handleMarking() {
        if (mediaPlayer == null) return;
        int pos = etLyrics.getSelectionStart();
        
        // Normalizamos los saltos de línea al recuperar el texto para evitar que colapse
        String text = etLyrics.getText().toString().replace("\r\n", "\n").replace("\r", "\n");
        if (text.isEmpty()) return;

        int lineStart = text.lastIndexOf("\n", pos - 1) + 1;
        int lineEnd = text.indexOf("\n", pos);
        if (lineEnd == -1) lineEnd = text.length();

        String fullLine = text.substring(lineStart, lineEnd);
        String cleanLine = fullLine.matches("^\\[\\d{2}:\\d{2}\\.\\d{2}\\].*") 
                           ? fullLine.substring(10).trim() : fullLine.trim();
        
        String time = formatTime(mediaPlayer.getCurrentPosition());
        String newLine = time + " " + cleanLine;

        String updatedText = text.substring(0, lineStart) + newLine + text.substring(lineEnd);
        etLyrics.setText(updatedText);
        
        int nextLinePos = lineStart + newLine.length() + 1;
        if (nextLinePos <= updatedText.length()) {
            etLyrics.setSelection(nextLinePos);
        } else {
            etLyrics.setSelection(updatedText.length());
        }
    }

    private String formatTime(int ms) {
        int m = (ms / 1000) / 60;
        int s = (ms / 1000) % 60;
        int mm = (ms % 1000) / 10;
        return String.format("[%02d:%02d.%02d]", m, s, mm);
    }

    private void updateTimer() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            int currentPos = mediaPlayer.getCurrentPosition();
            tvTime.setText(formatTime(currentPos));
            
            // Si el usuario no está arrastrando el control, la barra se mueve sola
            if (!isUserSeeking) {
                sbProgress.setProgress(currentPos);
            }
            
            tvTime.postDelayed(new Runnable() {
                @Override public void run() { updateTimer(); }
            }, 100);
        }
    }

    private void saveFile() {
        try {
            File f = new File("/sdcard/Download/" + songName + ".lrc");
            PrintWriter pw = new PrintWriter(new FileWriter(f));
            pw.print(etLyrics.getText().toString());
            pw.close();
            Toast.makeText(this, "Guardado: " + songName + ".lrc", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            mediaPlayer = MediaPlayer.create(this, uri);
            
            // Sincronizamos la duración total del archivo de audio con la barra
            if (mediaPlayer != null) {
                sbProgress.setMax(mediaPlayer.getDuration());
                sbProgress.setProgress(0);
            }
            
            // Intentamos extraer el nombre real del archivo MP3
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i != -1) {
                    songName = c.getString(i).replaceAll("\\.[^.]*$", "");
                }
                c.close();
            } else if (uri.getPath() != null) {
                String path = uri.getPath();
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    songName = path.substring(cut + 1).replaceAll("\\.[^.]*$", "");
                }
            }
            
            btnSelect.setText("🎵 " + songName);
            Toast.makeText(this, "Cargado: " + songName, Toast.LENGTH_SHORT).show();
        }
    }
}
