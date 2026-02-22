package executionLayer;

import org.openqa.selenium.By;


public class SelectorParser {

    public static By toBy(String selector) {

        String[] parts = selector.split(":", 2);


        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid selector format: " + selector);
        }

        String type = parts[0].trim();
        String value = parts[1].trim();


        return switch (type) {
            case "className" ->By.className(value);
            case "cssSelector" ->By.cssSelector(value);
            case "xpath" ->By.xpath(value);
            case "id" ->By.id(value);
            case "name" ->By.name(value);
            case "linkText" -> By.linkText(value);
            case "partialLinkText" -> By.partialLinkText(value);
            case "tagName" -> By.tagName(value);
            default -> throw new IllegalArgumentException("Unsupported selector type: " + type);
        };

     }
}
