package compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class Transaction {

    public final String id;
    public final String timestamp;
    public final Double amount;
    public final String currency;
    public final String country;
    public final String customerId;
    public final String accountType;
    public final String transactionType;
    public final Integer hourUtc;

    public Transaction(String id, String timestamp, Double amount, String currency, String country,
                       String customerId, String accountType, String transactionType, Integer hourUtc) {
        this.id = id;
        this.timestamp = timestamp;
        this.amount = amount;
        this.currency = currency;
        this.country = country;
        this.customerId = customerId;
        this.accountType = accountType;
        this.transactionType = transactionType;
        this.hourUtc = hourUtc;
    }

    public static List<Transaction> load(Path file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file.toFile());
        JsonNode array = root.get("transactions");
        if (array == null || !array.isArray()) {
            throw new IOException("JSON root needs a transactions array");
        }

        List<Transaction> transactions = new ArrayList<>();
        int index = 0;
        for (JsonNode node : array) {
            index++;
            if (!node.isObject()) {
                System.err.println("Skipping entry " + index + ": not a transaction object");
                continue;
            }
            transactions.add(read(node, index));
        }
        return transactions;
    }

    private static Transaction read(JsonNode node, int index) {
        String id = text(node, "transaction_id");
        if (id == null || id.isBlank()) {
            System.err.println("Transaction " + index + " has no transaction_id");
            id = "";
        }

        String timestamp = text(node, "timestamp");
        Integer hour = null;
        if (timestamp == null || timestamp.isBlank()) {
            System.err.println(label(id, index) + ": missing timestamp");
        } else {
            hour = hourInUtc(label(id, index), timestamp);
        }

        Double amount = readAmount(node, label(id, index));
        String currency = requiredText(node, "currency", label(id, index));
        String country = requiredText(node, "country", label(id, index));
        String customerId = requiredText(node, "customer_id", label(id, index));
        String accountType = requiredText(node, "account_type", label(id, index));
        String transactionType = requiredText(node, "transaction_type", label(id, index));

        return new Transaction(id, timestamp, amount, currency, country, customerId, accountType, transactionType, hour);
    }

    private static Double readAmount(JsonNode node, String label) {
        JsonNode value = node.get("amount");
        if (value == null || value.isNull()) {
            System.err.println(label + ": missing amount");
            return null;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().trim());
            } catch (NumberFormatException ex) {
                System.err.println(label + ": amount is not a number");
                return null;
            }
        }
        System.err.println(label + ": amount is not a number");
        return null;
    }

    private static String requiredText(JsonNode node, String field, String label) {
        String value = text(node, field);
        if (value == null) {
            System.err.println(label + ": missing " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    private static Integer hourInUtc(String label, String timestamp) {
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(timestamp);
            return parsed.withOffsetSameInstant(ZoneOffset.UTC).getHour();
        } catch (DateTimeException ex) {
            try {
                LocalDateTime local = LocalDateTime.parse(timestamp);
                System.err.println(label + ": timestamp has no timezone, using the clock hour as UTC");
                return local.getHour();
            } catch (DateTimeParseException ignored) {
                System.err.println(label + ": could not parse timestamp " + timestamp);
                return null;
            }
        }
    }

    private static String label(String id, int index) {
        if (id == null || id.isBlank()) {
            return "Transaction " + index;
        }
        return id;
    }

    String textValue(String field) {
        if ("transaction_id".equals(field)) {
            return id;
        }
        if ("timestamp".equals(field)) {
            return timestamp;
        }
        if ("timestamp.hour".equals(field)) {
            return hourUtc == null ? null : Integer.toString(hourUtc);
        }
        if ("amount".equals(field)) {
            if (amount == null) {
                return null;
            }
            return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
        }
        if ("currency".equals(field)) {
            return currency;
        }
        if ("country".equals(field)) {
            return country;
        }
        if ("customer_id".equals(field)) {
            return customerId;
        }
        if ("account_type".equals(field)) {
            return accountType;
        }
        if ("transaction_type".equals(field)) {
            return transactionType;
        }
        return null;
    }

    Double numberValue(String field) {
        if ("amount".equals(field)) {
            return amount;
        }
        if ("timestamp.hour".equals(field)) {
            return hourUtc == null ? null : hourUtc.doubleValue();
        }
        String text = textValue(field);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
