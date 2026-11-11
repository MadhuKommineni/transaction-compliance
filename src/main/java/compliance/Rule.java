package compliance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Rule {

    private static final Set<String> FIELDS = Set.of(
            "transaction_id",
            "timestamp",
            "timestamp.hour",
            "amount",
            "currency",
            "country",
            "customer_id",
            "account_type",
            "transaction_type"
    );

    private static final Set<String> OPERATORS = Set.of("gt", "lt", "eq", "neq", "in");

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public final String id;
    public final String name;
    public final String field;
    public final String operator;
    public final String value;
    public final String severity;

    public Rule(String id, String name, String field, String operator, String value, String severity) {
        this.id = id;
        this.name = name;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.severity = severity;
    }

    public static List<Rule> load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Rule> rules = new ArrayList<>();
        if (lines.isEmpty()) {
            System.err.println("Rules file is empty");
            return rules;
        }

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            Rule rule = parse(line, i + 1);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private static Rule parse(String line, int lineNumber) {
        List<String> cols = splitCsv(line);
        if (cols.size() < 6) {
            System.err.println("Skipping rules.csv line " + lineNumber + ": expected 6 columns");
            return null;
        }

        String id = cols.get(0).trim();
        String name = cols.get(1).trim();
        String field = cols.get(2).trim();
        String operator = cols.get(3).trim();
        String value = cols.get(4).trim();
        String severity = cols.get(5).trim();

        if (id.isEmpty()) {
            System.err.println("Skipping rules.csv line " + lineNumber + ": missing rule_id");
            return null;
        }
        if (!FIELDS.contains(field)) {
            System.err.println("Skipping " + id + ": unknown field " + field);
            return null;
        }
        if (!OPERATORS.contains(operator)) {
            System.err.println("Skipping " + id + ": unknown operator " + operator);
            return null;
        }
        if (("gt".equals(operator) || "lt".equals(operator)) && parseNumber(value) == null) {
            System.err.println("Skipping " + id + ": " + operator + " needs a numeric value");
            return null;
        }
        if (!SEVERITIES.contains(severity)) {
            System.err.println(id + ": severity \"" + severity + "\" is not LOW, MEDIUM, HIGH, or CRITICAL");
        }
        return new Rule(id, name, field, operator, value, severity);
    }

    static String describe(Transaction txn, List<Rule> rules) {
        List<Rule> hits = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.matches(txn)) {
                hits.add(rule);
            }
        }
        hits.sort(Comparator.comparing(rule -> rule.id));
        if (hits.isEmpty()) {
            return "none";
        }

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            Rule hit = hits.get(i);
            line.append(hit.id).append(" (").append(hit.severity).append(')');
        }
        return line.toString();
    }

    boolean matches(Transaction txn) {
        if ("gt".equals(operator) || "lt".equals(operator)) {
            Double left = txn.numberValue(field);
            Double right = parseNumber(value);
            if (left == null || right == null) {
                return false;
            }
            if ("gt".equals(operator)) {
                return left > right;
            }
            return left < right;
        }

        String left = txn.textValue(field);
        if (left == null) {
            return false;
        }
        if ("eq".equals(operator)) {
            return left.equals(value);
        }
        if ("neq".equals(operator)) {
            return !left.equals(value);
        }
        if ("in".equals(operator)) {
            for (String part : value.split(",", -1)) {
                String token = part.trim();
                if (!token.isEmpty() && left.equals(token)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static List<String> splitCsv(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cols.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cols.add(current.toString());
        return cols;
    }
}
