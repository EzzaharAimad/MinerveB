package com.quizagent.service;

import com.quizagent.model.Exercise;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class GptService {

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // Increase this if needed
            .build();

    public List<Exercise> generateFromText(String inputText, String evaluationType, String bloomLevel){
        try {
            JSONArray messages = new JSONArray();

            String systemPrompt;
            if ("certification".equalsIgnoreCase(evaluationType)) {
                systemPrompt = String.format(
                        "Vous êtes un expert en ingénierie pédagogique spécialisé en sujet de cours. À partir du contenu fourni, générez 5 exercices d’évaluation en e-learning selon le niveau de Bloom : %s.\n\n" +

                                "👉 Règles générales :\n" +
                                "- Le format des exercices doit correspondre STRICTEMENT au niveau cognitif ciblé.\n" +
                                "- Chaque exercice doit être réaliste, contextualisé, et bien détaillé avec noms, rôles, chiffres ou cas métier.\n" +
                                "- Le tout doit être renvoyé sous forme de tableau JSON de 5 objets. Chaque objet contient :\n" +
                                "  - title : titre de l'exercice\n" +
                                "  - instruction : consigne précise et détaillée\n" +
                                "  - options : liste d’options (OBLIGATOIRE pour QCM, QCU, texte à trous uniquement)\n" +
                                "  - answer : réponse attendue ou éléments de correction\n" +

                                "🔵 Si niveau = Mémoriser :\n" +
                                "- Générer exclusivement des QCM, QCU, appariements terme/définition, ou vrai/faux.\n" +
                                "- Exemple : définition de la négociation, rôle du BATNA, reconnaissance de termes clés.\n" +

                                "🟠 Si niveau = Comprendre :\n" +
                                "- Générer des exercices de classement, appariement logique, ou QCU avec justification.\n" +
                                "- Exemple : relier types de négociation à leur stratégie, ordonner les étapes d’un entretien.\n" +

                                "🟡 Si niveau = Appliquer :\n" +
                                "- Générer des mises en situation avec actions concrètes, complétions de dialogue, rédaction d’arguments.\n" +
                                "- Exemple : rédiger 2 arguments dans une simulation client, compléter une réplique dans un script.\n" +

                                "🔴 Si niveau = Analyser :\n" +
                                "- Générer des cas à décortiquer, erreurs à identifier, comparaisons de méthodes.\n" +
                                "- Exemple : identifier les failles dans un échange mal mené, proposer 2 pistes d’amélioration.\n" +

                                "🟣 Si niveau = Évaluer :\n" +
                                "- Générer des jugements critiques, comparaisons de postures, évaluations de simulation.\n" +
                                "- Exemple : comparer deux stratégies commerciales, évaluer un comportement de vente.\n" +

                                "🟢 Si niveau = Créer :\n" +
                                "- Générer des productions originales : rédiger un argumentaire, concevoir un outil ou scénario.\n" +
                                "- Exemple : créer un argumentaire structuré selon CAB, inventer une grille de traitement d’objections.\n",

                        bloomLevel
                );
            } else {
                systemPrompt =
                        "You are an educational quiz generator. Based on the course content, generate 5 multiple-choice questions.\n" +
                                "- Respond ONLY with a JSON array of 5 objects.\n" +
                                "- Each object must contain: title, instruction, options (list), answer.\n" +
                                "- No introduction, no explanation — JSON only.";
            }


            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", inputText));

            JSONObject body = new JSONObject()
                    .put("model", "gpt-4o")
                    .put("messages", messages);

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .post(RequestBody.create(MediaType.get("application/json"), body.toString()))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JSONObject responseJson = new JSONObject(responseBody);
            if (!responseJson.has("choices")) {
                throw new IOException("OpenAI API response does not contain 'choices': " + responseBody);
            }

            String content = responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // Extract only the JSON array from the GPT response
            int arrayStart = content.indexOf("[");
            int arrayEnd = content.lastIndexOf("]") + 1;
            String jsonOnly = content.substring(arrayStart, arrayEnd);

            JSONArray array = new JSONArray(jsonOnly);
            List<Exercise> results = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                Exercise ex = new Exercise();

                ex.setTitle(json.optString("title", "Untitled Question"));
                ex.setInstruction(json.optString("instruction", "Follow the instructions below."));

                JSONArray opts = json.optJSONArray("options");
                if (opts != null) {
                    List<String> options = new ArrayList<>();
                    for (int j = 0; j < opts.length(); j++) {
                        Object opt = opts.get(j);
                        if (opt instanceof String) {
                            options.add((String) opt);
                        } else if (opt instanceof JSONObject) {
                            options.add(((JSONObject) opt).toString()); // fallback if GPT made a nested object
                        } else {
                            options.add(opt.toString());
                        }
                    }
                    ex.setOptions(options);
                }

                ex.setAnswer(json.optString("answer", ""));
                results.add(ex);
            }

            return results;
        } catch (Exception e) {
            System.out.println("GPT ERROR: " + e.getMessage());

            List<Exercise> exercises = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                Exercise ex = new Exercise();
                ex.setTitle("Sample Question " + i);
                ex.setInstruction("Complete this task based on the content.");
                ex.setOptions(List.of("Option A", "Option B", "Option C"));
                ex.setAnswer("Option B");
                exercises.add(ex);
            }
            return exercises;
        }
    }
    public String simpleChat(String message) {
        try {
            JSONArray messages = new JSONArray();
            String systemPrompt = """
            Tu es Minerve, un assistant pédagogique. À partir d’une question d’évaluation ou d’un énoncé de mise en situation, réécris la même question en y ajoutant davantage de détails concrets.

            Ta mission :
            - Conserve exactement la structure et l’intention pédagogique de la question d’origine.
            - Ajoute des éléments réalistes : noms de personnes, contexte d’entreprise, produits/services précis, chiffres, objectifs, enjeux, contraintes, etc.
            - La nouvelle version doit être plus immersive et aider l’apprenant à mieux comprendre la problématique.

            Ne donne pas de conseils. Ne liste pas d’améliorations. Réponds uniquement avec une version enrichie de la question en une seule fois, sans explication.

            Réponds toujours en français.
        """;

            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", message));

            JSONObject body = new JSONObject()
                    .put("model", "gpt-4o")
                    .put("messages", messages);

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .post(RequestBody.create(MediaType.get("application/json"), body.toString()))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JSONObject responseJson = new JSONObject(responseBody);
            return responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            return "Erreur: " + e.getMessage();
        }
    }
}