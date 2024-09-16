package com.example.asistenteopenia;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private RequestQueue requestQueue;
    String ThreadID;
    TextView txtEstado;
    Button btsend;
    TextView txtPregunta;
    TextView txtChat;
    String lastMessageID;
    String TokenOpenAI = "token-here";

    String run_ID;
    Handler handler;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestQueue = Volley.newRequestQueue(this);
        txtEstado = findViewById(R.id.txtEstado);
        btsend = findViewById(R.id.btsend);
        txtPregunta = findViewById(R.id.txtPregunta);
        txtChat = findViewById(R.id.textView5);
        Button btnSpeak = findViewById(R.id.btnSpeak);
        handler = new Handler(Looper.getMainLooper());
        createThread();

        btnSpeak.setOnClickListener(v -> promptSpeechInput());
    }

    private void createThread() {
        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                Global.URL_CreatThread,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResp = new JSONObject(response);
                            ThreadID = jsonResp.getString("id").toString();

                            btsend.setEnabled(true);

                            txtEstado.setText("Chat ID: " + ThreadID);

                        } catch (JSONException e) {
                            txtEstado.setText("Error Creating Thread: " + e.toString());
                        }
                    }
                }, null) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getAuthHeaders(TokenOpenAI);
            }
        };
        requestQueue.add(stringRequest);
    }

    public Map<String, String> getAuthHeaders(String Token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + Token);
        headers.put("OpenAI-Beta", "assistants=v1");
        return headers;
    }


    public void sendPregunta(View v) {
        String texto = txtPregunta.getText().toString();
        if (!texto.equals("")) {
            txtChat.setText(txtChat.getText().toString() + "\n\n" + texto);
            txtPregunta.setText("");
            btsend.setEnabled(false);
            createMessage(texto);
        }
    }


    private void createMessage(String msg) {
        StringRequest stringRequest = new StringRequest(Request.Method.POST, Global.getUrlCreateMessage(ThreadID),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResp = new JSONObject(response);
                            lastMessageID = jsonResp.getString("id").toString();
                            txtEstado.setText("Mensaje ID: " + lastMessageID);
                            runMessage();
                        } catch (JSONException e) {
                            txtEstado.setText("Error CreatingMessage: " + e.getMessage());
                        }
                    }
                }, null) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getAuthHeaders(TokenOpenAI);
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                String requestBody = "{" +
                        "      \"role\": \"user\", " +
                        "      \"content\": \"" + msg + "\"}";
                return requestBody.getBytes(StandardCharsets.UTF_8);
            }

        };
        requestQueue.add(stringRequest);
    }

    private void runMessage() {
        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                Global.getURLRunMessage(ThreadID),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("Response", response);
                        try {
                            JSONObject jsonResp = new JSONObject(response);
                            run_ID = jsonResp.getString("id").toString();
                            txtEstado.setText("Ejecución ID: " + run_ID);
                            checkRunStatus();
                        } catch (JSONException e) {
                            txtEstado.setText("Error onRUN " + e.toString());
                        }
                    }
                }, null) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getAuthHeaders(TokenOpenAI);
            }
            @Override
            public byte[] getBody() throws AuthFailureError {
                String requestBody = "{ \"assistant_id\": \"" + Global.Assistant_ID + "\"," +
                        "      \"instructions\": \"" + Global.Instructions + "\"}";
                return requestBody.getBytes(StandardCharsets.UTF_8);
            }
        };
        requestQueue.add(stringRequest);
    }

    private void checkRunStatus() {
        Runnable statusChecker = new Runnable() {
            @Override
            public void run() {
                StringRequest checkStatusRequest = new StringRequest(Request.Method.GET,
                        Global.getURLCheckStatus(ThreadID, run_ID),
                        response -> {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                String status = jsonResponse.getString("status");
                                if ("completed".equals(status)) {
                                    getFinalMessage();
                                    txtEstado.setText("Respuesta Lista!");
                                } else {
                                    txtEstado.setText("Estado: " + status);
                                    handler.postDelayed(this, 1000);
                                }
                            } catch (Exception e) {
                                txtEstado.setText("Error GettingStatus: " + e.toString());
                            }
                        }, null) {
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        return getAuthHeaders(TokenOpenAI);
                    }
                };
                requestQueue.add(checkStatusRequest);
            }
        };
        handler.post(statusChecker);
    }

    private void getFinalMessage() {
        StringRequest getMessageRequest = new StringRequest(Request.Method.GET, Global.getURLGetMessage(ThreadID, lastMessageID),
                response -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray listaMsg = jsonResponse.getJSONArray("data");
                        JSONObject jsonMsg = listaMsg.getJSONObject(0);
                        JSONArray contentMsgs = jsonMsg.getJSONArray("content");
                        JSONObject contentMsg = contentMsgs.getJSONObject(0);
                        JSONObject contentTextMsg = contentMsg.getJSONObject("text");
                        String resp = contentTextMsg.getString("value").toString();

                        resp = resp.replaceAll("\\【.*?\\】", "");
                        txtEstado.setText("Chat ID: " + ThreadID);
                        txtChat.setText(txtChat.getText().toString() + "\n\n" + resp);
                        btsend.setEnabled(true);

                    } catch (JSONException e) {
                        txtEstado.setText("Error LastMessage: " + e.toString());
                    }
                }, null) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getAuthHeaders(TokenOpenAI);
            }
        };
        requestQueue.add(getMessageRequest);
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora");
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Error al iniciar reconocimiento de voz", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                txtPregunta.setText(spokenText);
                sendPregunta(null); // Enviar el texto al chatbot
            }
        }
    }
}
