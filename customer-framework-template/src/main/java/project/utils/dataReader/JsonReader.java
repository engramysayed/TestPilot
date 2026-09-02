package project.utils.dataReader;
import com.jayway.jsonpath.JsonPath;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import project.utils.Logs.LogsManager;

import java.io.FileReader;

public class JsonReader {

    private static final String TEST_DATA_PATH = "src/test/resources/test-data/";

    private String jsonReader;
    private String jsonFileName;

    public JsonReader(String jsonFileName) {
        this.jsonFileName = jsonFileName;
        try {
            JSONObject data = (JSONObject) new JSONParser()
                    .parse(new FileReader(TEST_DATA_PATH + jsonFileName));

            jsonReader = data.toJSONString();

        } catch (Exception e) {
            LogsManager.error(
                    "Error reading json file: " + jsonFileName + " | " + e.getMessage()
            );
            // avoid NPE later
            jsonReader = "{}";
        }
    }

    public String getJsonData(String jsonPath) {
        try {
            return JsonPath.read(jsonReader, jsonPath).toString();
        } catch (Exception e) {
            LogsManager.error(
                    "Error reading json path: " + jsonPath + " | " + e.getMessage()
            );
            return "";
        }
    }


}
