package com.subtitle.editor;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    VideoView videoView;
    View videoContainer;
    EditText etLyrics;
    Button btnMark, btnPlayPause, btnSelect, btnSave, btnFwd, btnRew;
    Button btnUp, btnDown, btnLeft, btnRight, btnLoadLrc;
    TextView tvTime;
    SeekBar sbProgress; 
    String songName = "LetraSincronizada";
    boolean isUserSeeking = false; 
    boolean isMediaLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        videoView = (VideoView) findViewById(R.id.videoView);
        videoContainer = findViewById(R.id.videoContainer);
        etLyrics = (EditText) findViewById(R.id.etLyrics);
        btnMark = (Button) findViewById(R.id.btnMark);
        btnPlayPause = (Button) findViewById(R.id.btnPlayPause);
        btnSelect = (Button) findViewById(R.id.btnSelectAudio);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnFwd = (Button) findViewById(R.id.btnFwd);
        btnRew = (Button) findViewById(R.id.btnRew);
        tvTime = (TextView) findViewById(R.id.tvCurrentTime);
        sbProgress = (SeekBar) findViewById(R.id.sbProgress); 

        btnUp = (Button) findViewById(R.id.btnUp);
        btnDown = (Button) findViewById(R.id.btnDown);
        btnLeft = (Button) findViewById(R.id.btnLeft);
        btnRight = (Button) findViewById(R.id.btnRight);
        btnLoadLrc = (Button) findViewById(R.id.btnLoadLrc);

        // Ocultamos el contenedor de video al iniciar hasta que se cargue un MP4
        videoContainer.setVisibility(View.GONE);

        etLyrics.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etLyrics.setSingleLine(false);

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*"); 
                String[] mimeTypes = {"audio/*", "video/*"};
                i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                startActivityForResult(i, 1);
            }
        });

        btnLoadLrc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                // Abrimos con filtro total para que no bloquee la extensión oculta .lrc
                i.setType("*/*"); 
                i.addCategory(Intent.CATEGORY_OPENABLE);
                
                // Forzamos los formatos de texto plano binario que suele usar Android para .lrc
                String[] mimeTypes = {"text/plain", "application/octet-stream", "text/*"};
                i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                
                startActivityForResult(i, 2);
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!isMediaLoaded) return;
                if (videoView.isPlaying()) {
                    videoView.pause(); 
                    btnPlayPause.setText("Play");
                } else {
                    videoView.start(); 
                    btnPlayPause.setText("Pause"); 
                    updateTimer();
                }
            }
        });

        btnRew.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isMediaLoaded) {
                    int newPos = videoView.getCurrentPosition() - 5000;
                    videoView.seekTo(Math.max(newPos, 0));
                    if (!isUserSeeking) sbProgress.setProgress(videoView.getCurrentPosition());
                }
            }
        });

        btnFwd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isMediaLoaded) {
                    int newPos = videoView.getCurrentPosition() + 5000;
                    videoView.seekTo(Math.min(newPos, videoView.getDuration()));
                    if (!isUserSeeking) sbProgress.setProgress(videoView.getCurrentPosition());
                }
            }
        });

        btnMark.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { handleMarking(); }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveFile(); }
        });

        btnLeft.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int pos = etLyrics.getSelectionStart();
                if (pos > 0) etLyrics.setSelection(pos - 1);
            }
        });

        btnRight.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int pos = etLyrics.getSelectionStart();
                if (pos < etLyrics.getText().length()) etLyrics.setSelection(pos + 1);
            }
        });

        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveCursorLine(-1); }
        });

        btnDown.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveCursorLine(1); }
        });

        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser &&
