Shopping List App
Purpose

A simple shopping list app that supports multiple stores, aisle-based sorting, optional item details, and fast entry while shopping.
The app is designed so items are entered quickly first, then enriched with optional data later.

Core Concepts

Active Store
The app has one Active Store at a time.
The Active Store can also be None (no store selected).

List Visibility
The shopping list always shows all items on the list.
Items are never hidden because of store selection.
Store selection only affects sorting and pricing, not visibility.

Item Data Model
Item (global)

Each item exists once and may have optional default data:
Name (required)
Default price (optional)
Size (optional)
Default quantity (optional)

Store-Specific Item Data
Each item may have store-specific overrides:
Aisle (optional)
Price override (optional)
Sorting Rules (Aisles)

When an Active Store is selected:
If the item has an aisle for that store → use it for sorting.
If not → assign the item to a default “Unassigned” aisle (e.g., aisle 999).

This ensures:
Store-mapped items appear in correct aisle order.
Unmapped items are still visible.

Adding vs Editing Items
Add Item (Fast Entry)
First-time item entry requires only the item name.
Item is immediately added to the shopping list.

Edit Item
Optional fields are edited in a separate Edit Item screen.
This screen allows:
Editing default item values
Assigning the item to one or more stores
Setting store-specific aisle and price overrides

Picklist
A picklist screen allows browsing all stored items.
Selecting an item from the picklist adds it back to the shopping list.

Checkout Rules
Checkout totals prices for items in the list.

Price selection order:
Store price override (if Active Store and override exists)
Item default price
$0.00 if no price exists

After checkout, purchased items are removed from the active list.

Design Goals
Fast, distraction-free entry while shopping
Store-aware aisle ordering
Flexible data without forcing extra input
Local database (offline-first)