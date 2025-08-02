# Transaction API Documentation

## Overview
The Transaction API supports retrieving all transactions or filtering transactions based on various criteria using GET requests with optional query parameters. All models now include automatic timestamp tracking for creation and modification times.

**Performance Optimized**: The API uses database-level filtering for efficient querying, especially with large datasets.

## Endpoints

### Get Transactions (with optional filtering)
**Endpoint:** `GET /api/transactions`

**Description:** Retrieve all transactions for the authenticated user, with optional filtering using query parameters.

**Query Parameters (all optional):**
- `category` (optional): Filter by transaction category
- `type` (optional): Filter by transaction type  
- `currency` (optional): Filter by currency (USD, INR, EUR)
- `transactionMethod` (optional): Filter by payment method
- `minAmount` (optional): Minimum transaction amount
- `maxAmount` (optional): Maximum transaction amount
- `startDate` (optional): Start date in YYYY-MM-DD format
- `endDate` (optional): End date in YYYY-MM-DD format
- `description` (optional): Filter by description (partial match, case-insensitive)
- `createdAfter` (optional): Filter records created after this timestamp (ISO format: yyyy-MM-ddTHH:mm:ss)
- `createdBefore` (optional): Filter records created before this timestamp (ISO format: yyyy-MM-ddTHH:mm:ss)
- `updatedAfter` (optional): Filter records updated after this timestamp (ISO format: yyyy-MM-ddTHH:mm:ss)
- `updatedBefore` (optional): Filter records updated before this timestamp (ISO format: yyyy-MM-ddTHH:mm:ss)

**Examples:**
```bash
# Get all transactions
GET /api/transactions

# Filter by category and amount range
GET /api/transactions?category=Food&minAmount=10&maxAmount=100

# Filter by date range and payment method
GET /api/transactions?startDate=2024-01-01&endDate=2024-12-31&transactionMethod=CREDIT_CARD

# Filter by description
GET /api/transactions?description=restaurant

# Filter by creation timestamp
GET /api/transactions?createdAfter=2024-01-01T00:00:00&createdBefore=2024-12-31T23:59:59

# Filter by last modified timestamp
GET /api/transactions?updatedAfter=2024-07-01T00:00:00
```

## Filter Criteria

### Currency Options
- `USD` - United States Dollar
- `INR` - Indian Rupee
- `EUR` - Euro

### Transaction Method Options
- `CASH` - Cash
- `CREDIT_CARD` - Credit Card
- `DEBIT_CARD` - Debit Card
- `BANK_TRANSFER` - Bank Transfer
- `UPI` - UPI
- `NET_BANKING` - Net Banking
- `PAYPAL` - PayPal
- `OTHER` - Other

## Error Handling

The API includes comprehensive error handling for invalid parameters:

### Invalid Enum Values
```bash
# Invalid currency
GET /api/transactions?currency=BITCOIN
# Response: 400 Bad Request
"Invalid parameter value: Invalid currency value: BITCOIN. Valid values are: [USD, INR, EUR]"

# Invalid transaction method
GET /api/transactions?transactionMethod=VENMO  
# Response: 400 Bad Request
"Invalid parameter value: Invalid transaction method: VENMO. Valid values are: [CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, UPI, NET_BANKING, PAYPAL, OTHER]"
```

### Invalid Date/Time Formats
```bash
# Invalid date format
GET /api/transactions?startDate=2024/01/15
# Response: 400 Bad Request
"Invalid parameter value: Invalid date format: 2024/01/15. Expected format: YYYY-MM-DD (e.g., 2024-01-15)"

# Invalid datetime format  
GET /api/transactions?createdAfter=2024-01-15%2014:30:00
# Response: 400 Bad Request
"Invalid parameter value: Invalid datetime format: 2024-01-15 14:30:00. Expected format: YYYY-MM-DDTHH:mm:ss (e.g., 2024-01-15T14:30:00)"
```

### Success Response Format
```json
[
  {
    "id": "64a5f8b123456789abcdef01",
    "userId": "64a5f8b123456789abcdef02",
    "description": "Lunch at restaurant",
    "amount": 25.50,
    "currency": "USD",
    "date": "2024-07-15T12:30:00.000Z",
    "category": "Food",
    "type": "expense",
    "notes": "Business lunch",
    "transactionMethod": "CREDIT_CARD",
    "createdAt": "2024-07-15T09:30:00",
    "updatedAt": "2024-07-15T10:15:00"
  }
]
```

## Model Changes
All models now include automatic timestamp fields:
- `createdAt`: Automatically set when the record is created
- `updatedAt`: Automatically updated whenever the record is modified

### Affected Models:
- **Transaction**: Tracks when transactions are created and modified
- **User**: Tracks when user accounts are created and profile updates
- **Token**: Tracks when authentication tokens are issued and updated

## Authentication
All filter endpoints require authentication. Include the JWT token in the Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

## Usage Examples:

**Get all transactions:**
```bash
curl -H "Authorization: Bearer <token>" \
  "/api/transactions"
```

**Filter by category and amount:**
```bash
curl -H "Authorization: Bearer <token>" \
  "/api/transactions?category=Food&minAmount=10&maxAmount=100"
```

**Filter by date range:**
```bash
curl -H "Authorization: Bearer <token>" \
  "/api/transactions?startDate=2024-01-01&endDate=2024-12-31"
```

## Notes
- All filter parameters are optional
- If no parameters are provided, all user transactions are returned
- Date filtering is inclusive (transactions on start and end dates are included)
- Description filtering is case-insensitive and supports partial matches
- Amount filtering supports range queries (between minAmount and maxAmount)
- This follows REST conventions where GET requests are used for retrieving/querying data
- **Performance**: Uses database-level filtering for optimal performance with large datasets
- **Scalability**: Memory usage and response time remain consistent regardless of total transaction count
