import { field, choice, lookup, money, type Field } from "./operations";
const status = choice("status", "Status", ["ACTIVE", "INACTIVE"]);
const bool = (name: string, label: string) =>
  choice(name, label, ["true", "false"]);
const optional = (name: string, label: string) =>
  field(name, label, { required: false });
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
        help: "Three uppercase letters. Code and decimal places cannot change after creation.",
      }),
      field("name", "Name"),
      optional("symbol", "Symbol"),
      field("decimalPlaces", "Decimal places", { type: "number" }),
      field("rateAgainstUsd", "Units received for USD 1", {
        type: "decimal",
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
      field("code", "Code"),
      field("name", "Name"),
      optional("category", "Category"),
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
      money("fixedAmount", "Fixed amount", false),
      money("percentage", "Percentage", false),
      money("minimumFee", "Minimum fee", false),
      money("maximumFee", "Maximum fee", false),
      money("minTransactionAmount", "Minimum transaction", false),
      money("maxTransactionAmount", "Maximum transaction", false),
      field("effectiveFrom", "Effective from", { type: "datetime-local" }),
      field("effectiveTo", "Effective to", {
        type: "datetime-local",
        required: false,
      }),
      field("priority", "Priority", { type: "number" }),
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
      field("code", "Code"),
      field("name", "Name"),
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
      ["agentRewardMode", "Reward mode"],
      ["agentRewardValue", "Reward value"],
      ["status", "Status"],
    ],
    fields: [
      lookup("billerId", "Biller", "/api/admin/billers"),
      field("code", "Code"),
      field("name", "Name"),
      currency,
      settlement,
      choice("agentRewardMode", "Agent reward mode", [
        "NONE",
        "DISCOUNT",
        "CASHBACK",
      ]),
      choice("agentRewardType", "Agent reward type", ["FIXED", "PERCENTAGE"]),
      money("agentRewardValue", "Reward value"),
      money("minimumCommission", "Minimum commission", false),
      money("maximumCommission", "Maximum commission", false),
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
        help: "For example: Standard or Custom.",
      }),
      field("totalCommissionPercentage", "Total commission %", {
        type: "decimal",
      }),
      field("agentCommissionPercentage", "Agent commission %", {
        type: "decimal",
      }),
      field("platformCommissionPercentage", "Platform commission %", {
        type: "decimal",
        help: "Agent % plus platform % must equal total commission %.",
      }),
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
      field("walletNumber", "Wallet number"),
      currency,
      choice("walletType", "Wallet type", ["SYSTEM", "BILLER"]),
      field("name", "Name"),
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
