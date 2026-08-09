/******************************************************
 * DASHBOARD TABLE – PURE JS (NO DATATABLES)
 * Features:
 * - Server side pagination (1000 rows per page)
 * - Status filter        (applied on the server)
 * - Date range filter    (applied on the server)
 * - Search               (applied on the server)
 * - Stable sorting (Start Time DESC, done by the server)
 * - Auto serial numbers (continue across pages)
 ******************************************************/

/* =======================
   GLOBAL STATE
======================= */
const PAGE_SIZE = 1000;

let activeStatusFilter = '';
let activeStartDate = '';   // yyyy-mm-dd, sent to the API as is
let activeEndDate = '';     // yyyy-mm-dd, sent to the API as is
let activeSearchQuery = '';

let currentPage = 0;
let totalPages = 0;
let totalElements = 0;
let isLoading = false;
let searchDebounceTimer = null;

/* =======================
   FORMATTERS
======================= */

function formatDate(isoDate) {
    if (!isoDate) return 'N/A';

    const d = new Date(isoDate);
    if (isNaN(d)) return 'N/A';

    return (
        String(d.getDate()).padStart(2, '0') + '/' +
        String(d.getMonth() + 1).padStart(2, '0') + '/' +
        d.getFullYear() + ', ' +
        String(d.getHours()).padStart(2, '0') + ':' +
        String(d.getMinutes()).padStart(2, '0') + ':' +
        String(d.getSeconds()).padStart(2, '0')
    );
}

function getStatusClass(status) {
    switch (status.toLowerCase()) {
        case 'complete': return 'bg-success';
        case 'terminate': return 'bg-danger';
        case 'quotafull': return 'bg-warning';
        default: return 'bg-secondary';
    }
}

/* =======================
   FETCH DATA (PAGED)
======================= */

function buildQuery(page, size) {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);

    if (activeStatusFilter) params.set('status', activeStatusFilter);
    if (activeStartDate) params.set('startDate', activeStartDate);
    if (activeEndDate) params.set('endDate', activeEndDate);
    if (activeSearchQuery) params.set('search', activeSearchQuery);

    return params.toString();
}

function handleUnauthorized() {
    alert("Session expired. Please login again.");
    localStorage.removeItem('jwtToken');
    window.location.href = "/login";
}

function mapRow(r) {
    return {
        projectId: r.projectId,
        uid: r.uid,
        status: r.status,
        startTime: formatDate(r.startTime),
        endTime: formatDate(r.endTime),
        ip: r.ipAddress,
        country: r.country
    };
}

function fetchSurveyResponses(page) {
    if (isLoading) return;

    isLoading = true;
    setPagingDisabled(true);

    fetch("/survey/api/survey-responses/paged?" + buildQuery(page, PAGE_SIZE))
        .then(res => {
            if (res.status === 401) {
                handleUnauthorized();
                return;
            }
            return res.json();
        })
        .then(data => {
            if (!data) return;

            currentPage = data.page;
            totalPages = data.totalPages;
            totalElements = data.totalElements;

            renderTable(data.content.map(mapRow), currentPage * data.size);
            renderPagination();
        })
        .catch(err => console.error("Error fetching data:", err))
        .finally(() => {
            isLoading = false;
            setPagingDisabled(false);
        });
}

// Reload from the first page – used whenever a filter changes
function applyFiltersAndRender() {
    fetchSurveyResponses(0);
}

/* =======================
   RENDER TABLE
======================= */

function renderTable(rows, serialOffset) {
    const tbody = document.querySelector('#example tbody');
    if (!tbody) return;

    tbody.innerHTML = '';

    rows.forEach((r, index) => {
        const tr = document.createElement('tr');

        tr.innerHTML = `
            <td>${serialOffset + index + 1}</td>
            <td>${r.projectId}</td>
            <td>${r.uid}</td>
            <td><span class="badge ${getStatusClass(r.status)}">${r.status}</span></td>
            <td>${r.startTime}</td>
            <td>${r.endTime}</td>
            <td>${r.ip}</td>
            <td>${r.country}</td>
        `;

        tbody.appendChild(tr);
    });
}

/* =======================
   RENDER PAGINATION
======================= */

function renderPagination() {
    const info = document.getElementById('paginationInfo');
    const indicator = document.getElementById('pageIndicator');

    if (info) {
        if (totalElements === 0) {
            info.innerText = 'No records found';
        } else {
            const from = currentPage * PAGE_SIZE + 1;
            const to = Math.min(from + PAGE_SIZE - 1, totalElements);
            info.innerText = `Showing ${from} to ${to} of ${totalElements} entries`;
        }
    }

    if (indicator) {
        indicator.innerText = `Page ${totalPages === 0 ? 0 : currentPage + 1} of ${totalPages}`;
    }

    setPagingDisabled(false);
}

function setPagingDisabled(loading) {
    const first = document.getElementById('firstPageBtn');
    const prev = document.getElementById('prevPageBtn');
    const next = document.getElementById('nextPageBtn');
    const last = document.getElementById('lastPageBtn');
    if (!first || !prev || !next || !last) return;

    const atStart = loading || currentPage <= 0;
    const atEnd = loading || currentPage >= totalPages - 1;

    first.disabled = atStart;
    prev.disabled = atStart;
    next.disabled = atEnd;
    last.disabled = atEnd;
}

/* =======================
   FILTER EVENTS
======================= */

document.addEventListener('click', e => {
    if (e.target.classList.contains('status-filter-option')) {
        e.preventDefault();
        e.stopPropagation();

        activeStatusFilter = e.target.dataset.status || '';
        document.getElementById('filterLabel').innerText =
            'Filter by Status: ' + e.target.innerText;

        document.getElementById('statusDropdownMenu').style.display = 'none';
        applyFiltersAndRender();
    }
});

// Toggle dropdown when clicking button OR its children
document.addEventListener('click', e => {
    const btn = document.getElementById('statusDropdownBtn');
    const menu = document.getElementById('statusDropdownMenu');

    if (btn.contains(e.target)) {
        e.preventDefault();
        e.stopPropagation();
        menu.style.display = menu.style.display === 'block' ? 'none' : 'block';
        return;
    }

    // Close when clicking outside
    if (!e.target.closest('.custom-dropdown')) {
        menu.style.display = 'none';
    }
});

// Date filters
document.addEventListener('change', e => {
    if (e.target.id === 'startDateFilter') {
        activeStartDate = e.target.value;
        applyFiltersAndRender();
    }

    if (e.target.id === 'endDateFilter') {
        activeEndDate = e.target.value;
        applyFiltersAndRender();
    }
});

// Clear date filter
document.addEventListener('click', e => {
    if (e.target.id === 'clearDateFilter') {
        activeStartDate = '';
        activeEndDate = '';
        document.getElementById('startDateFilter').value = '';
        document.getElementById('endDateFilter').value = '';
        applyFiltersAndRender();
    }
});

// Search input (debounced – every keystroke hits the server otherwise)
document.addEventListener('input', e => {
    if (e.target.id === 'searchInput') {
        const value = e.target.value.trim();
        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => {
            activeSearchQuery = value;
            applyFiltersAndRender();
        }, 400);
    }
});

// Clear search
document.addEventListener('click', e => {
    if (e.target.id === 'clearSearchBtn') {
        clearTimeout(searchDebounceTimer);
        activeSearchQuery = '';
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.value = '';
        }
        applyFiltersAndRender();
    }
});

/* =======================
   PAGINATION EVENTS
======================= */

document.addEventListener('click', e => {
    const target = e.target.closest('button');
    if (!target) return;

    switch (target.id) {
        case 'firstPageBtn':
            if (currentPage > 0) fetchSurveyResponses(0);
            break;
        case 'prevPageBtn':
            if (currentPage > 0) fetchSurveyResponses(currentPage - 1);
            break;
        case 'nextPageBtn':
            if (currentPage < totalPages - 1) fetchSurveyResponses(currentPage + 1);
            break;
        case 'lastPageBtn':
            if (currentPage < totalPages - 1) fetchSurveyResponses(totalPages - 1);
            break;
    }
});

/* =======================
   EXPORT (ALL FILTERED ROWS, NOT JUST THE CURRENT PAGE)
======================= */

function toCsvValue(value) {
    return `"${String(value === null || value === undefined ? '' : value).replace(/"/g, '""')}"`;
}

document.getElementById('exportExcelBtn').addEventListener('click', function () {
    const btn = this;
    const originalText = btn.innerText;
    btn.disabled = true;
    btn.innerText = 'Exporting...';

    // Export honours the active filters but ignores paging
    fetch("/survey/api/survey-responses/paged?" + buildQuery(0, Math.max(totalElements, PAGE_SIZE)))
        .then(res => {
            if (res.status === 401) {
                handleUnauthorized();
                return;
            }
            return res.json();
        })
        .then(data => {
            if (!data) return;

            const headers = Array.from(document.querySelectorAll('#example thead th'))
                .map(th => toCsvValue(th.innerText));

            const csv = [headers.join(',')];

            data.content.map(mapRow).forEach((r, index) => {
                csv.push([
                    index + 1, r.projectId, r.uid, r.status,
                    r.startTime, r.endTime, r.ip, r.country
                ].map(toCsvValue).join(','));
            });

            const csvBlob = new Blob([csv.join('\n')], {
                type: 'text/csv;charset=utf-8;'
            });

            const link = document.createElement('a');
            link.href = URL.createObjectURL(csvBlob);
            link.download = 'dashboard_export.csv';
            link.click();
            URL.revokeObjectURL(link.href);
        })
        .catch(err => console.error("Error exporting data:", err))
        .finally(() => {
            btn.disabled = false;
            btn.innerText = originalText;
        });
});

/* =======================
   INIT
======================= */

document.addEventListener('DOMContentLoaded', () => {
    fetchSurveyResponses(0);
});
