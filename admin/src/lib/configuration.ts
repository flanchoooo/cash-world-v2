import { field, choice, lookup, money, type Field } from "./operations";
const status = choice("status", "Status", ["ACTIVE", "INACTIVE"]);
const bool = (name: string, label: string) =>
  choice(name, label, ["true", "false"]);
const nonNegativeMoney = (name: string, label: string, required = false) =>
  money(name, label, required, {
    pattern: "[0-9]{1,15}(\\.[0-9]{1,4})?",
    maxLength: 20,
    title: "Enter 0 or a positive amount with up to 15 digits and 4 decimal places.",
  });
const percentage = (name: string, label: string, required = true) =>
  field(name, label, {
    type: "decimal",
    required,
    pattern: "(100(\\.0{1,4})?|[0-9]{1,2}(\\.[0-9]{1,4})?)",
    maxLength: 8,
    title: "Enter a percentage from 0 to 100 with up to 4 decimal places.",
  });
const currency = lookup("currencyId", "Currency", "/api/admin/currencies");
const settlement = lookup(
  "settlementWalletId",
  "Settlement wallet",
  "/api/admin/system-wallets",
);
export const configurations: {
  id: string;
  name: string;
  adminOnly: boolean;
  columns: [string, string][];
  fields: Field[];
  initial: Record<string, unknown>;
}[] = [
  {
    id: "wallet-types",
    name: "Wallet types",
    adminOnly: true,
    columns: [
      ["code", "Code"],
      ["name", "Name"],
      ["scope", "Scope"],
      ["status", "Status"],
    ],
    fields: [
      field("code", "Code", {
        maxLength: 40,
        help: "Unique code; it cannot change after creation.",
      }),
      field("name", "Name", { maxLength: 100 }),
      field("description", "Description", { required: false, maxLength: 500 }),
      choice("scope", "Scope", ["CUSTOMER", "BILLER", "SYSTEM"]),
      status,
    ],
    initial: { scope: "CUSTOMER", status: "ACTIVE" },
  },
  {
    id: "currencies",
    name: "Currencies",
    adminOnly: true,
    columns: [
      ["code", "Code"],
      ["name", "Name"],
      ["decimalPlaces", "Decimal places"],
      ["rateAgainstUsd", "Units per USD"],
      ["status", "Status"],
    ],
    fields: [
      field("code", "Currency code", {
        maxLength: 200,
        help: "Three uppercase letters. Code and decimal places cannot change after creation.",
      }),
      field("name", "Name", { maxLength: 200 }),
      field("symbol", "Symbol", { required: false, maxLength: 200 }),
      field("decimalPlaces", "Decimal places", {
        type: "number",
        min: 0,
        max: 4,
        step: 1,
        title: "Enter the supported currency decimal places.",
      }),
      field("rateAgainstUsd", "Units received for USD 1", {
        type: "decimal",
        pattern: "(?!0+(?:\\.0{1,10})?$)[0-9]{1,10}(\\.[0-9]{1,10})?",
        maxLength: 21,
        help: "Example: enter 13.500000 when USD 1 buys 13.50 units. USD must be 1.",
      }),
      status,
    ],
    initial: { decimalPlaces: 2, rateAgainstUsd: "", status: "ACTIVE" },
  },
  {
    id: "transaction-types",
    name: "Transaction types",
    adminOnly: true,
    columns: [
      ["code", "Code"],
      ["name", "Name"],
      ["category", "Category"],
      ["status", "Status"],
    ],
    fields: [
      field("code", "Code", { maxLength: 200 }),
      field("name", "Name", { maxLength: 200 }),
      field("category", "Category", { required: false, maxLength: 200 }),
      bool("isReversible", "Reversible"),
      bool("allowsFee", "Allows fees"),
      bool("allowsCommission", "Allows commissions"),
      status,
    ],
    initial: {
      status: "ACTIVE",
      isReversible: true,
      allowsFee: false,
      allowsCommission: false,
    },
  },
  {
    id: "fees",
    name: "Fee rules",
    adminOnly: false,
    columns: [
      ["transactionType", "Transaction type"],
      ["currency", "Currency"],
      ["calculationType", "Calculation"],
      ["fixedAmount", "Fixed amount"],
      ["percentage", "Percentage"],
      ["priority", "Priority"],
      ["status", "Status"],
    ],
    fields: [
      lookup(
        "transactionTypeId",
        "Transaction type",
        "/api/admin/transaction-types",
      ),
      lookup(
        "billerProductId",
        "Biller product",
        "/api/admin/biller-products",
        "id",
        false,
      ),
      choice(
        "customerType",
        "Customer type",
        ["INDIVIDUAL", "CORPORATE"],
        false,
      ),
      choice("agentOnly", "Agent only", ["true", "false"], false),
      currency,
      choice("calculationType", "Calculation", [
        "FIXED",
        "PERCENTAGE",
        "FIXED_PLUS_PERCENTAGE",
      ]),
      nonNegativeMoney("fixedAmount", "Fixed amount", false),
      percentage("percentage", "Percentage", false),
      nonNegativeMoney("minimumFee", "Minimum fee", false),
      nonNegativeMoney("maximumFee", "Maximum fee", false),
      nonNegativeMoney("minTransactionAmount", "Minimum transaction", false),
      nonNegativeMoney("maxTransactionAmount", "Maximum transaction", false),
      field("effectiveFrom", "Effective from", { type: "datetime-local" }),
      field("effectiveTo", "Effective to", {
        type: "datetime-local",
        required: false,
      }),
      field("priority", "Priority", { type: "number", step: 1 }),
      status,
    ],
    initial: {
      calculationType: "FIXED",
      fixedAmount: "0",
      priority: 0,
      status: "ACTIVE",
    },
  },
  {
    id: "billers",
    name: "Billers",
    adminOnly: false,
    columns: [
      ["code", "Code"],
      ["name", "Name"],
      ["category", "Category"],
      ["status", "Status"],
    ],
    fields: [
      field("code", "Code", { maxLength: 200 }),
      field("name", "Name", { maxLength: 200 }),
      choice("category", "Category", [
        "ELECTRICITY",
        "AIRTIME",
        "TV",
        "INTERNET",
        "INSURANCE",
        "SCHOOL",
        "MUNICIPALITY",
        "REMITTANCE",
        "OTHER",
      ]),
      settlement,
      bool("supportsValidation", "Supports validation"),
      bool("supportsReversal", "Supports reversal"),
      bool("supportsEnquiry", "Supports enquiry"),
      status,
    ],
    initial: {
      status: "ACTIVE",
      supportsValidation: true,
      supportsReversal: true,
      supportsEnquiry: true,
    },
  },
  {
    id: "biller-products",
    name: "Biller products",
    adminOnly: false,
    columns: [
      ["code", "Code"],
      ["name", "Name"],
      ["walletType", "Wallet type"],
      ["agentRewardMode", "Reward mode"],
      ["agentRewardValue", "Reward value"],
      ["status", "Status"],
    ],
    fields: [
      lookup("billerId", "Biller", "/api/admin/billers"),
      field("code", "Code", { maxLength: 200 }),
      field("name", "Name", { maxLength: 200 }),
      currency,
      lookup("walletTypeId", "Wallet type", "/api/admin/wallet-types"),
      settlement,
      choice("agentRewardMode", "Agent reward mode", [
        "NONE",
        "DISCOUNT",
        "CASHBACK",
      ]),
      choice("agentRewardType", "Agent reward type", ["FIXED", "PERCENTAGE"]),
      nonNegativeMoney("agentRewardValue", "Reward value", true),
      nonNegativeMoney("minimumCommission", "Minimum commission", false),
      nonNegativeMoney("maximumCommission", "Maximum commission", false),
      status,
    ],
    initial: {
      status: "ACTIVE",
      agentRewardMode: "NONE",
      agentRewardType: "FIXED",
      agentRewardValue: "0",
    },
  },
  {
    id: "product-commission-plans",
    name: "Product commission plans",
    adminOnly: false,
    columns: [
      ["productName", "Product"],
      ["currency", "Currency"],
      ["arrangementName", "Arrangement"],
      ["totalCommissionPercentage", "Total %"],
      ["agentCommissionPercentage", "Agent %"],
      ["platformCommissionPercentage", "Platform %"],
      ["status", "Status"],
    ],
    fields: [
      lookup("billerProductId", "Product", "/api/admin/biller-products"),
      field("arrangementName", "Arrangement name", {
        maxLength: 100,
        help: "For example: Standard or Custom.",
      }),
      percentage("totalCommissionPercentage", "Total commission %"),
      percentage("agentCommissionPercentage", "Agent commission %"),
      {
        ...percentage("platformCommissionPercentage", "Platform commission %"),
        help: "Agent % plus platform % must equal total commission %.",
      },
      status,
    ],
    initial: {
      arrangementName: "Standard",
      totalCommissionPercentage: "0",
      agentCommissionPercentage: "0",
      platformCommissionPercentage: "0",
      status: "ACTIVE",
    },
  },
  {
    id: "system-wallets",
    name: "System wallets",
    adminOnly: true,
    columns: [
      ["walletNumber", "Wallet"],
      ["name", "Name"],
      ["walletType", "Type"],
      ["currency", "Currency"],
      ["allowNegativeBalance", "Negative balance"],
      ["status", "Status"],
    ],
    fields: [
      field("walletNumber", "Wallet number", { maxLength: 200 }),
      currency,
      choice("walletType", "Wallet type", ["SYSTEM", "BILLER"]),
      field("name", "Name", { maxLength: 200 }),
      bool("allowNegativeBalance", "Allow negative balance"),
      choice("status", "Status", ["ACTIVE", "BLOCKED"]),
    ],
    initial: {
      walletType: "SYSTEM",
      allowNegativeBalance: false,
      status: "ACTIVE",
    },
  },
];
