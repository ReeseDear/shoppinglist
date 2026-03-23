# ShoppingList v2 — Functional Specification

## 1. Scope
This specification defines required behavior for data, sorting, pricing, and UI flow.  
If code behavior conflicts with this file, **this file wins**.

---

## 2. Core State

### 2.1 Active Store
- Exactly one Active Store may be selected at a time.
- Active Store may be null (no store selected).

---

## 3. Visibility Rules
- By default the shopping list displays all active list items regardless of store.
- Store selection affects sorting, price selection, and aisle labeling.
- A **"This store's items only"** filter toggle on the home screen restricts visibility:
    - When ON: only items that are **universal** (not flagged store-specific anywhere) OR **store-specific to the current store** are shown.
    - Items flagged store-specific for a *different* store are hidden.
    - This filter applies to both the home list and the picklist.
    - Filter state persists across sessions (saved to DataStore).

### 3.1 Store-Specific Items
- `StoreItem.isStoreSpecific` (DB column: `showIfAisleUnassigned`) marks an item as belonging to a specific store.
- Items added from the home screen are **universal by default** (`isStoreSpecific = false`).
- Users explicitly mark items as store-specific via the "Store-specific item" checkbox in Edit Item.
- When `isStoreSpecific = true` and the store-specific filter is ON, the item only appears when its store is active.

---

## 4. Data Model Rules

### 4.1 Item (global)
Each Item:
- Exists once globally
- Required:
    - id
    - name
- Optional:
    - defaultPrice
    - size
    - defaultQuantity

### 4.2 Store
Each Store has:
- id
- name

### 4.3 StoreItem (join / override)
Defines store-specific overrides:
- storeId
- itemId
- isStoreSpecific (Boolean, default false) — marks item as belonging only to this store
- Optional:
    - aisle
    - priceOverride

### 4.4 List Entry
Represents an item currently on the shopping list:
- itemId
- Optional:
    - quantityToBuy
    - checked / inCart

Does **NOT** store price or aisle (derived dynamically).

---

## 5. Sorting Logic (Critical)
When Active Store is selected, compute `sortAisle` per item:
- If `StoreItem.aisle` exists for Active Store → use it
- Else → assign `DEFAULT_AISLE`

`DEFAULT_AISLE = 0`

Sorting order:
1. `sortAisle` ascending
2. Item name ascending

If no Active Store:
- Sort by item name only

---

## 6. Price Resolution (Checkout)
When calculating price per item:
- If Active Store is selected AND `priceOverride` exists → use it
- Else if `defaultPrice` exists → use it
- Else → price = `0.00` (unpriced items contribute $0.00 to the total)

Checkout clears **all** items currently in the cart regardless of store or price.

---

## 7. UI Layout & Flow Rules

### 7.1 Launch / Home Screen Layout
On app launch, the UI shows only:
- App title (top left)
- Store selection dropdown (top area)
- Picklist icon (top area)
    - Opens the stored items picklist
- **In-the-Cart** section:
    - Section title
    - Action button positioned to the right of the section title
- **Need-to-Get** section:
    - Section title
    - “+” Add Item button positioned to the right of the section title
- Shopping list items

No other controls or menus are visible on launch.

---

### 7.2 Add Item (Fast Entry)
Purpose: fast entry of a **single item**, typically not already in the database.

- Invoked via “+” button in the **Need-to-Get** section header
- Only required input: item name
- As the user types:
    - The system performs a live lookup against existing Items
    - Matching Items are shown dynamically
- Selecting a matching Item:
    - Adds it to the shopping list
    - Increments quantity if already present
- Submitting new text:
    - Creates Item if it does not exist
    - Creates List Entry
- No optional fields shown here
- After adding an item:
    - The Add Item UI automatically returns to the previous screen

---

### 7.3 List Item Row Controls
Each list item row includes:
- Item name
- Quantity (if applicable)
- Edit button positioned on the right side of the row
- Short-press to move items between list sections:
    - **Need-to-Get → In-the-Cart** when checked / acquired
    - **In-the-Cart → Need-to-Get** when unchecked / returned
- Long-press **removes the List Entry only**
    - Database Items are never deleted via list UI

---

### 7.4 Edit Item Screen
This is the **only place** where optional data is edited.

Edit screen supports:
- Item name
- Store price override (selected store)
- Aisle (selected store) — tapping the field opens a dropdown of all existing aisles; typing filters the list
- "All stores" checkbox — applies the aisle to every store
- Default quantity, unit, size, default price, notes
- "Active" checkbox
- "Store-specific item" checkbox — marks the item as belonging only to the selected store
- Store switcher in the top bar to view/edit overrides for each store

---

## 8. Picklist Behavior
Purpose: selecting **multiple existing items** in one session.

- Picklist lists all Items in the database
- Selecting an item:
    - Adds it to the shopping list
    - Selection via short-tap
    - Increments quantity if already present
- Picklist remains active until the user exits
- A **Done** button exits the Picklist

(Picklist may be accessed via the Picklist icon or future UI, but behavior is fixed.)

---

## 9. Checkout Behavior
- Checkout totals all in-cart items using price rules in Section 6
- Unpriced items contribute $0.00 to the total
- On confirmation:
    - All items currently in the cart are removed from the active list
    - No automatic deletion of Items from the database

---

## 10. Non-Goals (Explicitly Out of Scope)
- Cloud sync
- User accounts
- Multiple simultaneous active stores
- Automatic aisle inference
- Historical analytics (future feature)

---

## 11. Development Rule
Features must be implemented as **vertical slices**:

Database → Repository → ViewModel → UI

Partial layers are not acceptable.

---

## How This File Is Used
- This file prevents re-design during coding
- New chats and features must reference sections here
- Changes require updating this file first
