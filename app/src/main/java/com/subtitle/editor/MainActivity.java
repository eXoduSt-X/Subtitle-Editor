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
                // CAMBIO ESTRATÉGICO: Usamos ACTION_OPEN_DOCUMENT para saltar el filtro estricto de MIME types
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                // Le indicamos que acepte cualquier flujo de datos plano
                i.setType("*/*"); 
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
                if (fromUser && isMediaLoaded) {
                    videoView.seekTo(progress);
                    tvTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { isUserSeeking = false; }
        });
    }

    private void moveCursorLine(int direction) {
        int pos = etLyrics.getSelectionStart();
        String text = etLyrics.getText().toString();
        if (text.isEmpty()) return;

        int currentLineStart = text.lastIndexOf("\n", pos - 1) + 1;
        int currentLineEnd = text.indexOf("\n", pos);
        if (currentLineEnd == -1) currentLineEnd = text.length();

        int column = pos - currentLineStart;

        if (direction == -1) {
            if (currentLineStart <= 0) return;
            int prevLineStart = text.lastIndexOf("\n", currentLineStart - 2) + 1;
            int prevLineEnd = currentLineStart - 1;
            int prevLineLength = prevLineEnd - prevLineStart;
            int targetPos = prevLineStart + Math.min(column, prevLineLength);
            etLyrics.setSelection(targetPos);
        } else if (direction == 1) {
            if (currentLineEnd >= text.length()) return;
            int nextLineStart = currentLineEnd + 1;
            int nextLineEnd = text.indexOf("\n", nextLineStart);
            if (nextLineEnd == -1) nextLineEnd = text.length();
            int nextLineLength = nextLineEnd - nextLineStart;
            int targetPos = nextLineStart + Math.min(column, nextLineLength);
            etLyrics.setSelection(targetPos);
        }
    }

    private void handleMarking() {
        if (!isMediaLoaded) return;
        int pos = etLyrics.getSelectionStart();
        
        String text = etLyrics.getText().toString().replace("\r\n", "\n").replace("\r", "\n");
        if (text.isEmpty()) return;

        int lineStart = text.lastIndexOf("\n", pos - 1) + 1;
        int lineEnd = text.indexOf("\n", pos);
        if (lineEnd == -1) lineEnd = text.length();

        String fullLine = text.substring(lineStart, lineEnd);
        String cleanLine = fullLine.matches("^\\[\\d{2}:\\d{2}\\.\\d{2}\\].*") 
                           ? fullLine.substring(10).trim() : fullLine.trim();
        
        String time = formatTime(videoView.getCurrentPosition());
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
        if (isMediaLoaded && videoView.isPlaying()) {
            int currentPos = videoView.getCurrentPosition();
            tvTime.setText(formatTime(currentPos));
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
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            
            if (requestCode == 1) {
                extractSongName(uri);
                btnSelect.setText("🎬 " + songName);
                
                videoContainer.setVisibility(View.VISIBLE);
                videoContainer.requestLayout();
                
                videoView.setVideoURI(uri);
                isMediaLoaded = true;
                
                videoView.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(android.media.MediaPlayer mp) {
                        sbProgress.setMax(videoView.getDuration());
                        sbProgress.setProgress(0);
                        
                        if (mp.getVideoWidth() == 0 || mp.getVideoHeight() == 0) {
                            videoContainer.setVisibility(View.GONE);
                        } else {
                            videoView.seekTo(1);
                        }
                    }
                });
                
                Toast.makeText(this, "Cargado: " + songName, Toast.LENGTH_SHORT).show();
            } 
            else if (requestCode == 2) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    br.close();
                    is.close();
                    
                    String lrcContent = sb.toString();
                    
                    // Limpieza del BOM "ÿþ" inicial de codificaciones Windows en los .lrc
                    if (lrcContent.startsWith("ÿþ") || lrcContent.startsWith("\uFEFF") || lrcContent.startsWith("\uFFFE")) {
                        lrcContent = lrcContent.substring(2);
                    } else if (lrcContent.length() > 0 && (lrcContent.charAt(0) == 'ÿ' || lrcContent.charAt(0) == 'þ')) {
                        lrcContent = lrcContent.substring(1);
                    }
                    
                    lrcContent = lrcContent.replace("\r\n", "\n").replace("\r", "\n");
                    etLyrics.setText(lrcContent);
                    
                    extractSongName(uri);
                    btnLoadLrc.setText("📂 " + songName);
                    Toast.makeText(this, "LRC Cargado: " + songName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error al leer LRC", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void extractSongName(Uri uri) {
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
    }
}
