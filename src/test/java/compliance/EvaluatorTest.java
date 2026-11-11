package compliance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    @Test
    void sampleFilesMatchTheBrief() throws Exception {
        List<Transaction> transactions = Transaction.load(Path.of("data", "transactions.json"));
        List<Rule> rules = Rule.load(Path.of("data", "rules.csv"));

        String expected = """
                Transaction    Breached Rules
                TXN001    R001 (HIGH), R003 (MEDIUM), R006 (MEDIUM)
                TXN002    R002 (HIGH), R005 (LOW), R007 (LOW), R008 (LOW)
                TXN003    R005 (LOW)
                TXN004    R001 (HIGH), R002 (HIGH), R003 (MEDIUM), R006 (MEDIUM), R007 (LOW)
                TXN005    R002 (HIGH), R008 (LOW)
                TXN006    R005 (LOW)
                TXN007    R001 (HIGH), R004 (CRITICAL), R006 (MEDIUM), R007 (LOW)
                TXN008    R001 (HIGH), R002 (HIGH), R003 (MEDIUM), R006 (MEDIUM), R007 (LOW), R008 (LOW)
                """;

        assertEquals(expected, Main.render(transactions, rules));
    }

    @Test
    void boundariesCaseAndListMembership() {
        Rule large = new Rule("R001", "large_transaction", "amount", "gt", "10000", "HIGH");
        Rule small = new Rule("R005", "small_payment", "amount", "lt", "1000", "LOW");
        Rule afterHours = new Rule("R003", "after_hours_transaction", "timestamp.hour", "gt", "22", "MEDIUM");
        Rule countries = new Rule("R002", "high_risk_country", "country", "in", "IR,KP,SY", "HIGH");
        Rule wire = new Rule("R006", "wire_transfer", "transaction_type", "eq", "WIRE_TRANSFER", "MEDIUM");
        Rule currency = new Rule("R008", "non_usd_transaction", "currency", "neq", "USD", "LOW");

        Transaction atLimit = txn(10000.0, "USD", "US", "PERSONAL", "PAYMENT", 22);
        Transaction justOver = txn(10000.01, "usd", "ir", "PERSONAL", "wire_transfer", 23);
        Transaction smallPayment = txn(999.99, "EUR", "KP", "PERSONAL", "PAYMENT", 0);

        assertFalse(large.matches(atLimit));
        assertFalse(small.matches(atLimit));
        assertFalse(afterHours.matches(atLimit));
        assertTrue(large.matches(justOver));
        assertTrue(afterHours.matches(justOver));
        assertFalse(countries.matches(justOver));
        assertFalse(wire.matches(justOver));
        assertTrue(currency.matches(justOver));
        assertTrue(small.matches(smallPayment));
        assertTrue(countries.matches(smallPayment));
        assertFalse(currency.matches(atLimit));
    }

    @Test
    void breachedRulesAreSortedById() {
        Transaction txn = txn(15000.0, "EUR", "US", "PERSONAL", "WIRE_TRANSFER", 10);
        List<Rule> rules = List.of(
                new Rule("R008", "non_usd_transaction", "currency", "neq", "USD", "LOW"),
                new Rule("R006", "wire_transfer", "transaction_type", "eq", "WIRE_TRANSFER", "MEDIUM"),
                new Rule("R001", "large_transaction", "amount", "gt", "10000", "HIGH")
        );

        assertEquals("R001 (HIGH), R006 (MEDIUM), R008 (LOW)", Rule.describe(txn, rules));
    }

    @Test
    void quietTransactionIsNone() throws Exception {
        List<Rule> rules = Rule.load(Path.of("data", "rules.csv"));
        Transaction txn = txn(5000.0, "USD", "US", "PERSONAL", "PAYMENT", 12);
        assertEquals("none", Rule.describe(txn, rules));
    }

    @Test
    void quotedCountryListSurvivesTheCsv() throws Exception {
        List<Rule> rules = Rule.load(Path.of("data", "rules.csv"));
        Rule countries = null;
        for (Rule rule : rules) {
            if ("R002".equals(rule.id)) {
                countries = rule;
            }
        }
        assertEquals("IR,KP,SY", countries.value);
        assertEquals(8, rules.size());
    }

    @Test
    void brokenRulesAreLeftOut(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("rules.csv");
        Files.writeString(csv, """
                rule_id,rule_name,field,operator,value,severity
                ,missing,amount,gt,1,LOW
                R9,bad_op,amount,between,1,LOW
                R8,bad_field,region,eq,US,LOW
                R7,bad_number,amount,gt,ten,HIGH
                short,row
                R3,ok,amount,gt,10,HIGH

                """);

        List<Rule> rules = Rule.load(csv);
        assertEquals(1, rules.size());
        assertEquals("R3", rules.get(0).id);
    }

    @Test
    void hourIsTakenInUtcAndABadTimestampDoesNotMatch(@TempDir Path dir) throws Exception {
        Path json = dir.resolve("transactions.json");
        Files.writeString(json, """
                {
                  "transactions": [
                    {
                      "transaction_id": "BAD",
                      "timestamp": "yesterday",
                      "amount": 50,
                      "currency": "USD",
                      "country": "US",
                      "customer_id": "C",
                      "account_type": "PERSONAL",
                      "transaction_type": "PAYMENT"
                    },
                    {
                      "transaction_id": "OFFSET",
                      "timestamp": "2024-01-16T01:30:00+02:00",
                      "amount": 50,
                      "currency": "USD",
                      "country": "US",
                      "customer_id": "C",
                      "account_type": "PERSONAL",
                      "transaction_type": "PAYMENT"
                    }
                  ]
                }
                """);

        List<Transaction> transactions = Transaction.load(json);
        Rule afterHours = new Rule("R003", "after_hours_transaction", "timestamp.hour", "gt", "22", "MEDIUM");

        assertEquals(2, transactions.size());
        assertEquals(null, transactions.get(0).hourUtc);
        assertFalse(afterHours.matches(transactions.get(0)));
        assertEquals(23, transactions.get(1).hourUtc);
        assertTrue(afterHours.matches(transactions.get(1)));
    }

    @Test
    void nonObjectEntriesAreSkippedAndMissingFieldsDoNotMatch(@TempDir Path dir) throws Exception {
        Path json = dir.resolve("transactions.json");
        Files.writeString(json, """
                {
                  "transactions": [
                    null,
                    42,
                    {
                      "transaction_id": "PARTIAL",
                      "timestamp": "2024-01-15T00:00:00Z",
                      "country": "US",
                      "customer_id": "C",
                      "account_type": "PERSONAL",
                      "transaction_type": "PAYMENT"
                    }
                  ]
                }
                """);

        List<Transaction> transactions = Transaction.load(json);

        assertEquals(1, transactions.size());
        Transaction partial = transactions.get(0);
        assertNull(partial.amount);
        assertNull(partial.currency);
        assertFalse(new Rule("R1", "non_usd", "currency", "neq", "USD", "LOW").matches(partial));
        assertFalse(new Rule("R2", "large", "amount", "gt", "0", "HIGH").matches(partial));
    }

    @Test
    void invalidJsonAndMissingTransactionsArrayFailToLoad(@TempDir Path dir) throws Exception {
        Path invalidJson = dir.resolve("invalid.json");
        Files.writeString(invalidJson, "{");
        assertThrows(IOException.class, () -> Transaction.load(invalidJson));

        Path missingArray = dir.resolve("missing-array.json");
        Files.writeString(missingArray, "{\"data\":[]}");
        IOException error = assertThrows(IOException.class, () -> Transaction.load(missingArray));
        assertEquals("JSON root needs a transactions array", error.getMessage());
    }

    @Test
    void unsupportedSeverityIsKeptAndReported(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("rules.csv");
        Files.writeString(csv, """
                rule_id,rule_name,field,operator,value,severity
                R9,usd_payment,currency,eq,USD,URGENT
                """);

        List<Rule> rules = Rule.load(csv);
        Transaction txn = txn(50.0, "USD", "US", "PERSONAL", "PAYMENT", 12);

        assertEquals(1, rules.size());
        assertEquals("URGENT", rules.get(0).severity);
        assertEquals("R9 (URGENT)", Rule.describe(txn, rules));
    }

    @Test
    void amountTextComparisonsUseNormalizedNumberText() {
        Transaction txn = txn(15000.00, "USD", "US", "PERSONAL", "PAYMENT", 12);
        Rule equal = new Rule("R1", "amount", "amount", "eq", "15000", "HIGH");
        Rule inList = new Rule("R2", "amount_in_list", "amount", "in", "10000, 15000 ", "LOW");
        Rule withTrailingZeros = new Rule("R3", "amount_with_zeros", "amount", "eq", "15000.00", "LOW");

        assertTrue(equal.matches(txn));
        assertTrue(inList.matches(txn));
        assertFalse(withTrailingZeros.matches(txn));
    }

    @Test
    void duplicateTransactionIdsArePrintedTwice() {
        Transaction first = txn(50.0, "USD", "US", "PERSONAL", "PAYMENT", 12);
        Transaction second = txn(60.0, "USD", "US", "PERSONAL", "PAYMENT", 13);

        assertEquals(
                "Transaction    Breached Rules\nT    none\nT    none\n",
                Main.render(List.of(first, second), List.of())
        );
    }

    private static Transaction txn(double amount, String currency, String country,
                                   String accountType, String transactionType, int hour) {
        return new Transaction(
                "T",
                "2024-01-15T00:00:00Z",
                amount,
                currency,
                country,
                "C",
                accountType,
                transactionType,
                hour
        );
    }
}
