package compliance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Path transactionsFile;
        Path rulesFile;
        if (args.length == 0) {
            transactionsFile = Path.of("data", "transactions.json");
            rulesFile = Path.of("data", "rules.csv");
        } else if (args.length == 2) {
            transactionsFile = Path.of(args[0]);
            rulesFile = Path.of(args[1]);
        } else {
            System.err.println("Usage: Main <transactions.json> <rules.csv>");
            System.exit(1);
            return;
        }

        if (!Files.isRegularFile(transactionsFile) || !Files.isRegularFile(rulesFile)) {
            System.err.println("Could not find both input files.");
            System.err.println("transactions: " + transactionsFile.toAbsolutePath());
            System.err.println("rules: " + rulesFile.toAbsolutePath());
            System.exit(1);
            return;
        }

        try {
            List<Transaction> transactions = Transaction.load(transactionsFile);
            List<Rule> rules = Rule.load(rulesFile);
            System.out.print(render(transactions, rules));
        } catch (Exception ex) {
            System.err.println("Could not read input: " + ex.getMessage());
            System.exit(1);
        }
    }

    public static String render(List<Transaction> transactions, List<Rule> rules) {
        StringBuilder out = new StringBuilder();
        out.append("Transaction    Breached Rules");
        for (Transaction txn : transactions) {
            out.append('\n');
            out.append(txn.id == null ? "" : txn.id);
            out.append("    ");
            out.append(Rule.describe(txn, rules));
        }
        out.append('\n');
        return out.toString();
    }
}
