package llmLayer;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.PropertyReader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class LLMClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiKey;
    private final String model;

    public LLMClient() {
        this.apiKey = PropertyReader.getProperty("GEMINI_API_KEY");
        this.model = PropertyReader.getProperty("GEMINI_MODEL");
    }

    public String generateTextWithOptionalImage(String prompt, byte[] imageBytes, String mimeType) throws Exception {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();

        JSONObject userContent = new JSONObject();
        userContent.put("role", "user");

        JSONArray parts = new JSONArray();

        //IMAGE part
        if (imageBytes != null && imageBytes.length > 0) {
            JSONObject inlineData = new JSONObject();
            inlineData.put("mimeType", (mimeType == null || mimeType.isBlank()) ? "image/png" : mimeType);
            inlineData.put("data", Base64.getEncoder().encodeToString(imageBytes));

            JSONObject imagePart = new JSONObject();
            imagePart.put("inlineData", inlineData);
            parts.put(imagePart);
        }

        //TEXT prompt
        parts.put(new JSONObject().put("text", prompt));

        userContent.put("parts", parts);
        contents.put(userContent);

        body.put("contents", contents);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " -> " + response.body());
        }

        return extractText(response.body());
    }

    private String extractText(String apiResponseJson) {
        JSONObject root = new JSONObject(apiResponseJson);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) return "";

        JSONObject c0 = candidates.optJSONObject(0);
        if (c0 == null) return "";

        JSONObject content = c0.optJSONObject("content");
        if (content == null) return "";

        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p != null) sb.append(p.optString("text", ""));
        }
        return sb.toString().trim();
    }

}
