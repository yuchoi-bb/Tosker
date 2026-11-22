/**
 * 단축버튼 아이콘.
 * 안드로이드 앱이 저장하는 키를 그대로 받아 Material Symbols 이름으로 바꾼다.
 * "alpha:A" 처럼 알파벳 항목은 글자로 그린다.
 */

export const LETTER_PREFIX = 'alpha:';

export const GROUPS = [
  ['건강·운동', [
    ['medication', 'medication'],
    ['water', 'water_drop'],
    ['drink', 'local_drink'],
    ['fitness', 'fitness_center'],
    ['run', 'directions_run'],
    ['walk', 'directions_walk'],
    ['bike', 'directions_bike'],
    ['pool', 'pool'],
    ['meditation', 'self_improvement'],
    ['heart', 'monitor_heart'],
    ['favorite', 'favorite'],
    ['spa', 'spa'],
    ['bedtime', 'bedtime'],
    ['hotel', 'hotel'],
  ]],
  ['음식', [
    ['restaurant', 'restaurant'],
    ['dining', 'local_dining'],
    ['fastfood', 'fastfood'],
    ['lunch', 'lunch_dining'],
    ['dinner', 'dinner_dining'],
    ['ramen', 'ramen_dining'],
    ['ricebowl', 'rice_bowl'],
    ['setmeal', 'set_meal'],
    ['bakery', 'bakery_dining'],
    ['pizza', 'local_pizza'],
    ['egg', 'egg'],
    ['cookie', 'cookie'],
    ['icecream', 'icecream'],
    ['cake', 'cake'],
    ['breakfast', 'free_breakfast'],
    ['cafe', 'local_cafe'],
    ['tea', 'emoji_food_beverage'],
    ['kitchen', 'kitchen'],
  ]],
  ['술·음료', [
    ['liquor', 'liquor'],
    ['wine', 'wine_bar'],
    ['beer', 'sports_bar'],
    ['cocktail', 'local_bar'],
    ['nightlife', 'nightlife'],
    ['nodrinks', 'no_drinks'],
  ]],
  ['업무·공부', [
    ['work', 'work'],
    ['email', 'email'],
    ['call', 'call'],
    ['computer', 'computer'],
    ['laptop', 'laptop'],
    ['edit', 'edit'],
    ['description', 'description'],
    ['assignment', 'assignment'],
    ['book', 'menu_book'],
    ['school', 'school'],
    ['calculate', 'calculate'],
    ['business', 'business'],
  ]],
  ['돈', [
    ['money', 'attach_money'],
    ['card', 'credit_card'],
    ['savings', 'savings'],
    ['receipt', 'receipt'],
    ['wallet', 'account_balance_wallet'],
  ]],
  ['집안일', [
    ['home', 'home'],
    ['cleaning', 'cleaning_services'],
    ['laundry', 'local_laundry_service'],
    ['cart', 'shopping_cart'],
    ['bag', 'shopping_bag'],
    ['store', 'local_grocery_store'],
    ['pets', 'pets'],
    ['build', 'build'],
    ['delete', 'delete'],
  ]],
  ['이동', [
    ['car', 'directions_car'],
    ['gas', 'local_gas_station'],
    ['train', 'train'],
    ['flight', 'flight'],
    ['map', 'map'],
    ['place', 'place'],
  ]],
  ['기타', [
    ['star', 'star'],
    ['flag', 'flag'],
    ['check', 'check_circle'],
    ['alarm', 'alarm'],
    ['schedule', 'schedule'],
    ['today', 'today'],
    ['event', 'event'],
    ['repeat', 'repeat'],
    ['notifications', 'notifications'],
    ['camera', 'photo_camera'],
    ['music', 'music_note'],
    ['headphones', 'headphones'],
    ['tv', 'tv'],
    ['game', 'sports_esports'],
    ['person', 'person'],
  ]],
  ['알파벳', Array.from({ length: 26 }, (_, i) => {
    const letter = String.fromCharCode(65 + i);
    return [`${LETTER_PREFIX}${letter}`, null];
  })],
];

const BY_KEY = new Map();
for (const [, entries] of GROUPS) {
  for (const [key, symbol] of entries) BY_KEY.set(key, symbol);
}

export function letterOf(key) {
  return String(key || '').startsWith(LETTER_PREFIX)
    ? String(key).slice(LETTER_PREFIX.length).slice(0, 1)
    : null;
}

export function symbolOf(key) {
  return BY_KEY.get(key) || 'check_circle';
}

export const DEFAULT_KEY = 'check';

/** 아이콘 하나를 그리는 DOM 노드를 만든다. */
export function render(key, { size = 22, color = 'currentColor' } = {}) {
  const letter = letterOf(key);
  const el = document.createElement('span');
  if (letter) {
    el.textContent = letter;
    el.style.fontWeight = '700';
    el.style.fontSize = `${size - 2}px`;
    el.style.lineHeight = '1';
  } else {
    el.className = 'material-symbols-rounded';
    el.textContent = symbolOf(key);
    el.style.fontSize = `${size}px`;
  }
  el.style.color = color;
  return el;
}
