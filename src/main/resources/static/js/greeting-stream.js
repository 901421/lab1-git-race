// Greeting history (bonus): shows new greetings live, pushed by the server over SSE
document.addEventListener('DOMContentLoaded', function () {
    const list = document.getElementById('historyList');
    // Without data-last-id the history could not be read, so there are no live updates
    if (!list || !list.dataset.lastId || !window.EventSource) return;

    const MAX_ITEMS = 10;
    const empty = document.getElementById('historyEmpty');
    const seen = new Set(Array.from(list.children, item => item.dataset.id));

    const source = new EventSource('/api/greetings/stream?after=' + encodeURIComponent(list.dataset.lastId));
    source.addEventListener('greeting', function (event) {
        // A greeting stored while the page was loading can arrive twice
        if (seen.has(event.lastEventId)) return;
        seen.add(event.lastEventId);

        const greeting = JSON.parse(event.data);
        const item = document.createElement('li');
        item.dataset.id = event.lastEventId;
        // textContent, never innerHTML: a name is always shown as text
        const name = document.createElement('strong');
        name.textContent = greeting.name;
        const locale = document.createElement('span');
        locale.textContent = greeting.locale;
        const time = document.createElement('span');
        time.textContent = formatUtc(greeting.timestamp);
        item.append(name, ' · ', locale, ' · ', time);

        list.prepend(item);
        while (list.children.length > MAX_ITEMS) list.lastElementChild.remove();
        if (empty) empty.remove();
    });

    // Same format as the server renders: "2026-09-24 08:46 UTC"
    function formatUtc(iso) {
        return iso.slice(0, 10) + ' ' + iso.slice(11, 16) + ' UTC';
    }
});
