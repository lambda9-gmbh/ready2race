import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

namespace Ready2Race;

// ============================================================================
// Models
// ============================================================================

model BankAccountDto {
  id: string; // UUID
  holder: string;
  iban: string;
  bic: string;
  bank: string;
}

model AssignedBankAccountDto {
  assigned?: BankAccountDto;
}

model BankAccountRequest {
  holder: string;
  iban: string;
  bic: string;
  bank: string;
}

model AssignBankAccountRequest {
  bankAccount?: string; // UUID
  event?: string; // UUID
}

// ============================================================================
// Routes
// ============================================================================

@route("/assignedBankAccount")
@tag("BankAccount")
interface AssignedBankAccountRoutes {
  // Get assigned bank account
  @get
  @route("")
  getAssigned(
    @query event?: string, // UUID
  ): AssignedBankAccountDto;

  // Assign bank account
  @put
  @route("")
  assignAccount(
    @body request: AssignBankAccountRequest,
  ): void;
}

@route("/bankAccount")
@tag("BankAccount")
interface BankAccountRoutes {
  // Create bank account
  @post
  @route("")
  addAccount(
    @body request: BankAccountRequest,
  ): BankAccountDto;

  // Get paginated bank accounts
  @get
  @route("")
  listBankAccounts(
    // Pagination parameters
  ): BankAccountDto[];

  // Update bank account
  @put
  @route("/{bankAccountId}")
  updateAccount(
    @path bankAccountId: string, // UUID
    @body request: BankAccountRequest,
  ): BankAccountDto;

  // Delete bank account
  @delete
  @route("/{bankAccountId}")
  deleteAccount(
    @path bankAccountId: string, // UUID
  ): void;
}
