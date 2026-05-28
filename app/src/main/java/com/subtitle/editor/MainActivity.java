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
    SeekBar sbProgress; // Nueva barra de progreso
    String songName = "LetraSincronizada";
    boolean isUserSeeking = false; // Bandera para evitar conflictos al arrastrar la barra

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
        sbProgress = (SeekBar) findViewById(R.id.sbProgress); // Vinculación de la barra

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

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
            }
        });
    }

    private void handleMarking() {
        if (mediaPlayer == null) return;
        int pos = etLyrics.getSelectionStart();
        
        // Normalizamos los saltos de línea al recuperar el texto para evitar que colapse
        String text = etLyrics.getText().toString().replace("\r\n", "\n").replace("\r", "\n");
        if (text.isEmpty()) return;

        int lineStart = text.lastIndexOf
