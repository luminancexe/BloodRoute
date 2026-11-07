/**
 * BloodRoute — Frontend Map & Emergency Dispatcher Controller
 * Interacts with pure Java REST endpoints on localhost:8080.
 * Features real-time step-by-step Dijkstra simulation visualization and interactive proximity routing.
 */

let map;
let networkData = { nodes: [], edges: [] };
let donorsData = [];
let routePolyline = null;
let activeRoadRouteLayer = null;
let activeRoadRouteCasing = null;
let activeRouteAbortController = null;
let nodeMarkers = {};
let donorMarkers = [];
let requesterPulseLayer = null;

// Active state
let currentMatches = [];
let currentRequesterNodeId = 0;
let selectedDonorId = null;

// Simulation State
let simRunning = false;
let simSpeed = 15; // ms per step (fastest, smooth exploration)
let simTimeoutId = null;
let simLayers = []; // temporary visual layers (active nodes, exploring edges)

// Initialize application on DOM load
document.addEventListener('DOMContentLoaded', () => {
    initMap();
    setupEventListeners();
    loadNetworkAndDonors();
});

/**
 * Initializes the Leaflet Real Map centered over Bangladesh with standard OpenStreetMap tiles
 */
function initMap() {
    // Center of Bangladesh: 23.85, 90.25 (Zoom 7 shows all 8 divisions)
    map = L.map('map', {
        zoomControl: true,
        attributionControl: true
    }).setView([23.85, 90.25], 7);

    // Standard OpenStreetMap tiles (Reliable, fast, global)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors | BloodRoute Bangladesh'
    }).addTo(map);

    // Invalidate size to guarantee immediate rendering
    setTimeout(() => {
        if (map) map.invalidateSize();
    }, 250);
}

/**
 * Loads graph network and donor registry from Java server
 */
async function loadNetworkAndDonors() {
    try {
        const [netRes, donorRes] = await Promise.all([
            fetch('/api/network'),
            fetch('/api/donors')
        ]);

        networkData = await netRes.json();
        donorsData = await donorRes.json();

        // Update Header Facility Count
        const hospCount = networkData.nodes.filter(n => n.isHospital).length;
        const countEl = document.getElementById('facilityCountText');
        if (countEl) countEl.textContent = `${hospCount} Facilities Active`;

        populateDropdowns();
        initHospitalSearchbar();
        renderNetworkGraph();
        renderDonorsOnMap(donorsData);
    } catch (err) {
        console.error('Error fetching data from BloodRoute Java server:', err);
    }
}

/**
 * Populates location selectors in UI grouped by Division
 */
function populateDropdowns() {
    const requesterSelect = document.getElementById('requesterSelect');
    const regLocationSelect = document.getElementById('regLocation');

    if (requesterSelect) requesterSelect.innerHTML = '';
    if (regLocationSelect) regLocationSelect.innerHTML = '';

    // Group nodes by division
    const divisions = {};
    networkData.nodes.forEach(node => {
        const div = node.division || 'Dhaka';
        if (!divisions[div]) divisions[div] = [];
        divisions[div].push(node);
    });

    Object.keys(divisions).forEach(divName => {
        const optgroupReq = document.createElement('optgroup');
        optgroupReq.label = `${divName} Division`;

        const optgroupReg = document.createElement('optgroup');
        optgroupReg.label = `${divName} Division`;

        divisions[divName].forEach(node => {
            if (requesterSelect) {
                const option = document.createElement('option');
                option.value = node.id;
                option.textContent = `${node.name} (${node.area})`;
                if (node.id === 0) option.selected = true;
                optgroupReq.appendChild(option);
            }

            if (regLocationSelect) {
                const regOption = document.createElement('option');
                regOption.value = node.id;
                regOption.textContent = `${node.name} (${node.area})`;
                optgroupReg.appendChild(regOption);
            }
        });

        if (requesterSelect) requesterSelect.appendChild(optgroupReq);
        if (regLocationSelect) regLocationSelect.appendChild(optgroupReg);
    });
}

/**
 * Interactive Hospital Searchbar & Combobox Controller
 */
let hospitalSearchState = {
    selectedNodeId: 0,
    focusedIndex: -1,
    filteredNodes: []
};

function initHospitalSearchbar() {
    const input = document.getElementById('hospitalSearchInput');
    const dropdown = document.getElementById('hospitalDropdownList');
    const clearBtn = document.getElementById('clearHospitalSearchBtn');
    const container = document.getElementById('hospitalSearchableContainer');
    const locateBtn = document.getElementById('locateHospitalBtn');

    if (!input || !dropdown) return;

    // Set initial default hospital (Node 0 - DMCH)
    selectHospital(0, false);

    // Focus / Click opens dropdown
    input.addEventListener('focus', () => {
        renderHospitalDropdown(input.value.trim());
        showDropdown();
        input.select();
    });

    input.addEventListener('click', () => {
        renderHospitalDropdown(input.value.trim());
        showDropdown();
    });

    // Input live filtering
    input.addEventListener('input', (e) => {
        const query = e.target.value.trim();
        if (query.length > 0) {
            clearBtn.classList.remove('hidden');
        } else {
            clearBtn.classList.add('hidden');
        }
        renderHospitalDropdown(query);
        showDropdown();
    });

    // Clear search button
    if (clearBtn) {
        clearBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            input.value = '';
            clearBtn.classList.add('hidden');
            renderHospitalDropdown('');
            input.focus();
            showDropdown();
        });
    }

    // Keyboard navigation (Arrow Up, Arrow Down, Enter, Escape)
    input.addEventListener('keydown', (e) => {
        if (dropdown.classList.contains('hidden')) {
            if (e.key === 'ArrowDown' || e.key === 'Enter') {
                showDropdown();
                renderHospitalDropdown(input.value.trim());
                e.preventDefault();
            }
            return;
        }

        const items = dropdown.querySelectorAll('.dropdown-item');
        if (!items.length) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            hospitalSearchState.focusedIndex = (hospitalSearchState.focusedIndex + 1) % items.length;
            updateItemFocus(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            hospitalSearchState.focusedIndex = (hospitalSearchState.focusedIndex - 1 + items.length) % items.length;
            updateItemFocus(items);
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (hospitalSearchState.focusedIndex >= 0 && hospitalSearchState.focusedIndex < hospitalSearchState.filteredNodes.length) {
                const targetNode = hospitalSearchState.filteredNodes[hospitalSearchState.focusedIndex];
                selectHospital(targetNode.id, true);
            } else if (hospitalSearchState.filteredNodes.length > 0) {
                selectHospital(hospitalSearchState.filteredNodes[0].id, true);
            }
        } else if (e.key === 'Escape') {
            hideDropdown();
        }
    });

    // Locate button on selected hospital card
    if (locateBtn) {
        locateBtn.addEventListener('click', () => {
            selectHospital(hospitalSearchState.selectedNodeId, true);
        });
    }

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (container && !container.contains(e.target) && e.target !== locateBtn) {
            hideDropdown();
        }
    });
}

function showDropdown() {
    const dropdown = document.getElementById('hospitalDropdownList');
    if (dropdown) dropdown.classList.remove('hidden');
}

function hideDropdown() {
    const dropdown = document.getElementById('hospitalDropdownList');
    if (dropdown) dropdown.classList.add('hidden');
    hospitalSearchState.focusedIndex = -1;
}

function updateItemFocus(items) {
    items.forEach((item, idx) => {
        if (idx === hospitalSearchState.focusedIndex) {
            item.classList.add('focused');
            item.scrollIntoView({ block: 'nearest' });
        } else {
            item.classList.remove('focused');
        }
    });
}

/**
 * Filters and renders matching hospitals grouped by division
 */
function renderHospitalDropdown(query = '') {
    const dropdown = document.getElementById('hospitalDropdownList');
    if (!dropdown) return;

    dropdown.innerHTML = '';
    const qLower = query.toLowerCase();

    // Filter nodes: hospitals match first, then intersections
    let matches = networkData.nodes.filter(node => {
        if (!query) return true;
        const nameMatch = node.name.toLowerCase().includes(qLower);
        const areaMatch = node.area.toLowerCase().includes(qLower);
        const divMatch = (node.division || '').toLowerCase().includes(qLower);
        const contactMatch = (node.contact || '').toLowerCase().includes(qLower);
        const addressMatch = (node.address || '').toLowerCase().includes(qLower);
        return nameMatch || areaMatch || divMatch || contactMatch || addressMatch;
    });

    // Sort: hospitals first, then alphabetical
    matches.sort((a, b) => {
        if (a.isHospital && !b.isHospital) return -1;
        if (!a.isHospital && b.isHospital) return 1;
        return a.name.localeCompare(b.name);
    });

    hospitalSearchState.filteredNodes = matches;
    hospitalSearchState.focusedIndex = -1;

    if (matches.length === 0) {
        dropdown.innerHTML = `<div class="no-results-msg">
            No medical centers found matching <strong>"${escapeHtml(query)}"</strong>.<br>
            <span style="font-size: 11px; color: #64748b; margin-top: 4px; display: inline-block;">Try searching by hospital or area (e.g. Mirpur, Dhanmondi, Uttara, Banani, Panthapath)</span>
        </div>`;
        return;
    }

    // Group matching nodes by division
    const grouped = {};
    matches.forEach(node => {
        const div = node.division || 'Dhaka';
        if (!grouped[div]) grouped[div] = [];
        grouped[div].push(node);
    });

    Object.keys(grouped).forEach(divName => {
        const groupHeader = document.createElement('div');
        groupHeader.className = 'dropdown-group-header';
        groupHeader.innerHTML = `<span>${escapeHtml(divName)} Division</span> <span>${grouped[divName].length} facilities</span>`;
        dropdown.appendChild(groupHeader);

        grouped[divName].forEach(node => {
            const item = document.createElement('div');
            item.className = 'dropdown-item' + (node.id === hospitalSearchState.selectedNodeId ? ' selected' : '');
            item.dataset.id = node.id;

            // Highlight matching text in name
            let highlightedName = escapeHtml(node.name);
            if (query) {
                const regex = new RegExp(`(${escapeRegExp(query)})`, 'gi');
                highlightedName = highlightedName.replace(regex, '<mark>$1</mark>');
            }

            const phoneSnippet = node.contact ? `<span class="item-phone">${escapeHtml(node.contact.split(',')[0].trim())}</span>` : '';
            const badgeText = node.isHospital ? '<span class="item-badge">Hospital</span>' : '<span class="item-badge" style="background: rgba(71,85,105,0.3); color:#94a3b8;">Highway</span>';

            item.innerHTML = `
                <div class="item-main-row">
                    <span class="item-title">${highlightedName}</span>
                    ${badgeText}
                </div>
                <div class="item-meta-row">
                    <span>${escapeHtml(node.area)}</span>
                    ${phoneSnippet}
                </div>
            `;

            item.addEventListener('click', () => {
                selectHospital(node.id, true);
            });

            dropdown.appendChild(item);
        });
    });
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeRegExp(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Selects a hospital, updates UI inputs and card, and centers map
 */
function selectHospital(nodeId, panMap = true) {
    hospitalSearchState.selectedNodeId = nodeId;
    currentRequesterNodeId = nodeId;

    const node = networkData.nodes.find(n => n.id === nodeId);
    if (!node) return;

    // Sync underlying select
    const requesterSelect = document.getElementById('requesterSelect');
    if (requesterSelect) requesterSelect.value = nodeId;

    // Update searchbar input
    const input = document.getElementById('hospitalSearchInput');
    if (input) {
        input.value = `${node.name} (${node.area})`;
        const clearBtn = document.getElementById('clearHospitalSearchBtn');
        if (clearBtn) clearBtn.classList.remove('hidden');
    }

    // Update Quick Info Card
    const nameEl = document.getElementById('selectedHospitalName');
    const areaEl = document.getElementById('selectedHospitalArea');
    const phoneEl = document.getElementById('selectedHospitalPhone');
    const cardEl = document.getElementById('selectedHospitalCard');

    if (nameEl) nameEl.textContent = node.name;
    if (areaEl) areaEl.textContent = `${node.area}, ${node.division || 'Dhaka'}`;
    if (phoneEl) {
        const phone = node.contact ? node.contact.split(',')[0].trim() : '+8802-HOTLINE';
        phoneEl.textContent = phone;
    }
    if (cardEl) {
        cardEl.style.borderColor = '#DC2626';
        setTimeout(() => { cardEl.style.borderColor = 'var(--border-subtle)'; }, 800);
    }

    hideDropdown();

    // Map Pan and Popup Highlight
    if (panMap && map) {
        highlightRequesterOnMap(node);
        const targetZoom = Math.max(map.getZoom(), 14);
        map.flyTo([node.lat, node.lng], targetZoom, { duration: 0.8 });
        setTimeout(() => {
            if (nodeMarkers[nodeId]) {
                nodeMarkers[nodeId].openPopup();
            }
        }, 850);
    }
}

/**
 * Draws a distinct emergency beacon pulse on the requester hospital
 */
function highlightRequesterOnMap(node) {
    if (requesterPulseLayer) {
        map.removeLayer(requesterPulseLayer);
        requesterPulseLayer = null;
    }

    requesterPulseLayer = L.circleMarker([node.lat, node.lng], {
        radius: 20,
        color: '#DC2626',
        weight: 2,
        fillColor: '#EF4444',
        fillOpacity: 0.25,
        className: 'pulse-active-requester'
    }).addTo(map);
}

/**
 * Global helper to set a clicked hospital as the emergency requester in the UI
 */
window.selectHospitalAsRequester = function(nodeId) {
    selectHospital(nodeId, false);
};

/**
 * Draws road network edges and node landmarks onto the real map
 */
function renderNetworkGraph() {
    // 1. Draw Road Connections (Edges)
    networkData.edges.forEach(edge => {
        const srcNode = networkData.nodes.find(n => n.id === edge.src);
        const destNode = networkData.nodes.find(n => n.id === edge.dest);
        if (srcNode && destNode) {
            L.polyline([
                [srcNode.lat, srcNode.lng],
                [destNode.lat, destNode.lng]
            ], {
                color: '#334155',
                weight: 2.5,
                opacity: 0.5,
                dashArray: '3, 6'
            }).addTo(map);
        }
    });

    // 2. Draw Nodes (Hospitals & Highway Intersections) with Professional Vector Pins
    networkData.nodes.forEach(node => {
        const isHosp = node.isHospital;
        let iconHtml;

        if (isHosp) {
            // Modern Medical Hospital Pin (Crisp SVG vector pin with emergency cross)
            iconHtml = `
            <div class="hospital-marker-pin" title="${escapeHtml(node.name)}">
                <svg width="28" height="36" viewBox="0 0 30 38" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <defs>
                        <filter id="hPinShadow-${node.id}" x="-25%" y="-15%" width="150%" height="140%">
                            <feDropShadow dx="0" dy="2.5" stdDeviation="2.5" flood-color="#000000" flood-opacity="0.45"/>
                        </filter>
                        <linearGradient id="hPinGrad-${node.id}" x1="15" y1="0" x2="15" y2="38" gradientUnits="userSpaceOnUse">
                            <stop offset="0%" stop-color="#3B82F6"/>
                            <stop offset="60%" stop-color="#1D4ED8"/>
                            <stop offset="100%" stop-color="#1E3A8A"/>
                        </linearGradient>
                    </defs>
                    <path d="M15 37.5C15 37.5 28 24 28 14.5C28 6.5 22.2 0.5 15 0.5C7.8 0.5 2 6.5 2 14.5C2 24 15 37.5 15 37.5Z" 
                          fill="url(#hPinGrad-${node.id})" stroke="#FFFFFF" stroke-width="1.5" filter="url(#hPinShadow-${node.id})"/>
                    <circle cx="15" cy="14" r="8.5" fill="#FFFFFF"/>
                    <rect x="13.5" y="8.5" width="3" height="11" rx="1" fill="#DC2626"/>
                    <rect x="9.5" y="12.5" width="11" height="3" rx="1" fill="#DC2626"/>
                </svg>
            </div>`;
        } else {
            // Subtle highway intersection dot pin
            iconHtml = `
            <div class="intersection-marker-pin" title="${escapeHtml(node.name)}">
                <svg width="16" height="22" viewBox="0 0 18 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M9 23.5C9 23.5 17 15 17 9C17 4.5 13.4 0.5 9 0.5C4.6 0.5 1 4.5 1 9C1 15 9 23.5 9 23.5Z" fill="#1E293B" stroke="#64748B" stroke-width="1.2"/>
                    <circle cx="9" cy="9" r="3.5" fill="#94A3B8"/>
                </svg>
            </div>`;
        }

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'custom-node-icon',
            iconSize: isHosp ? [28, 36] : [16, 22],
            iconAnchor: isHosp ? [14, 35] : [8, 21]
        });

        const marker = L.marker([node.lat, node.lng], { icon: customIcon }).addTo(map);

        const phoneStr = node.contact ? node.contact.split(',')[0].trim() : '';
        const popupHtml = `<div class="hospital-popup" style="font-family: inherit; min-width: 210px; padding: 2px;">
            <div style="font-weight: 700; color: #F8FAFC; font-size: 13px; margin-bottom: 4px;">${node.name}</div>
            <div style="font-size: 11.5px; color: #94A3B8; line-height: 1.45;">
                <div><strong style="color: #CBD5E1;">Division:</strong> ${node.division || 'Dhaka'}</div>
                <div><strong style="color: #CBD5E1;">Area:</strong> ${node.area}</div>
                ${node.address ? `<div style="margin-top: 3px;"><strong style="color: #CBD5E1;">Address:</strong> ${node.address}</div>` : ''}
                ${phoneStr ? `<div style="margin-top: 4px;"><strong style="color: #CBD5E1;">Hotline:</strong> <a href="tel:${phoneStr}" style="color: #38BDF8; font-weight: 700; text-decoration: none;">${phoneStr}</a></div>` : ''}
            </div>
            ${isHosp ? `<button onclick="window.selectHospitalAsRequester(${node.id})" style="margin-top: 9px; width: 100%; background: #DC2626; color: white; border: none; padding: 6px 10px; border-radius: 6px; cursor: pointer; font-weight: 700; font-size: 11px; transition: background 0.15s;">Set as Requester Location</button>` : ''}
        </div>`;

        marker.bindPopup(popupHtml);
        nodeMarkers[node.id] = marker;
    });
}

/**
 * Renders donor markers across Bangladesh with Facebook-style glossy blood droplet pins
 */
function renderDonorsOnMap(donors) {
    donorMarkers.forEach(m => map.removeLayer(m));
    donorMarkers = [];

    donors.forEach(donor => {
        const node = networkData.nodes.find(n => n.id === donor.nodeId);
        if (!node) return;

        // Micro-jitter to prevent markers from stacking exactly on top of node center
        const offsetLat = (Math.random() - 0.5) * 0.0022;
        const offsetLng = (Math.random() - 0.5) * 0.0022;

        const isAvail = donor.isAvailable;
        const mainColor = isAvail ? '#DC2626' : '#64748B';
        const darkColor = isAvail ? '#991B1B' : '#334155';
        const lightColor = isAvail ? '#EF4444' : '#94A3B8';

        // Facebook Blood Donation Signature Glossy Teardrop SVG Pin
        const iconHtml = `
        <div class="donor-fb-pin ${isAvail ? 'available' : 'busy'}" id="donor-pin-${donor.id}" title="${donor.name} (${donor.bloodType})">
            <svg width="32" height="40" viewBox="0 0 34 42" fill="none" xmlns="http://www.w3.org/2000/svg">
                <defs>
                    <filter id="fbShadow-${donor.id}" x="-25%" y="-15%" width="150%" height="140%">
                        <feDropShadow dx="0" dy="2.5" stdDeviation="2.5" flood-color="#000000" flood-opacity="0.45"/>
                    </filter>
                    <linearGradient id="fbGrad-${donor.id}" x1="17" y1="2" x2="17" y2="40" gradientUnits="userSpaceOnUse">
                        <stop offset="0%" stop-color="${lightColor}"/>
                        <stop offset="45%" stop-color="${mainColor}"/>
                        <stop offset="100%" stop-color="${darkColor}"/>
                    </linearGradient>
                </defs>
                <path d="M17 2C17 2 32 19 32 28C32 35.8 25.3 41 17 41C8.7 41 2 35.8 2 28C2 19 17 2 17 2Z" 
                      fill="url(#fbGrad-${donor.id})" stroke="#FFFFFF" stroke-width="1.8" filter="url(#fbShadow-${donor.id})"/>
                <path d="M17 5C17 5 26 19 26 26C25.5 21 21 12 17 5Z" fill="#FFFFFF" fill-opacity="0.35"/>
                <text x="17" y="32" text-anchor="middle" font-family="'Inter', sans-serif" 
                      font-weight="900" font-size="11.5" fill="#FFFFFF" letter-spacing="-0.3">${donor.bloodType}</text>
            </svg>
        </div>`;

        const donorIcon = L.divIcon({
            html: iconHtml,
            className: 'custom-donor-icon',
            iconSize: [32, 40],
            iconAnchor: [16, 39]
        });

        const marker = L.marker([node.lat + offsetLat, node.lng + offsetLng], { icon: donorIcon }).addTo(map);
        marker.donorId = donor.id;

        marker.bindPopup(`
            <div style="font-family: inherit; min-width: 190px; padding: 2px;">
                <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px;">
                    <strong style="color: #F8FAFC; font-size: 13px;">${donor.name}</strong>
                    <span style="background: rgba(220, 38, 38, 0.25); color: #FCA5A5; font-weight: 800; font-size: 11px; padding: 2px 6px; border-radius: 4px;">${donor.bloodType}</span>
                </div>
                <div style="font-size: 11.5px; color: #94A3B8; line-height: 1.45;">
                    <div><strong style="color: #CBD5E1;">Status:</strong> ${donor.isAvailable ? '<span style="color:#10B981;">● Available for Emergency</span>' : '<span style="color:#F87171;">● Busy</span>'}</div>
                    <div><strong style="color: #CBD5E1;">Direct Phone:</strong> <a href="tel:${donor.phone}" style="color: #38BDF8; font-weight: 700; text-decoration: none;">${donor.phone}</a></div>
                    <div><strong style="color: #CBD5E1;">Location:</strong> ${donor.area} (${donor.division || 'Dhaka'})</div>
                </div>
            </div>
        `);

        donorMarkers.push(marker);
    });
}

/**
 * Sets up button clicks, dropdown changes, and modal controls
 */
function setupEventListeners() {
    // Blood Group Dropdown Selector
    const bloodSelect = document.getElementById('bloodTypeSelect');
    if (bloodSelect) {
        bloodSelect.addEventListener('change', (e) => {
            const hidden = document.getElementById('selectedBloodType');
            if (hidden) hidden.value = e.target.value;
        });
    }

    // Find Nearest Compatible Donor CTA
    const findBtn = document.getElementById('findDonorBtn');
    if (findBtn) {
        findBtn.addEventListener('click', handleEmergencySearch);
    }

    // Register Modal Open/Close Controls
    const modal = document.getElementById('registerModal');
    const openBtn = document.getElementById('headerRegisterDonorBtn');
    const closeBtn = document.getElementById('closeRegisterModalBtn');
    const cancelBtn = document.getElementById('cancelRegisterModalBtn');

    if (openBtn && modal) {
        openBtn.addEventListener('click', () => modal.classList.remove('hidden'));
    }
    if (closeBtn && modal) {
        closeBtn.addEventListener('click', () => modal.classList.add('hidden'));
    }
    if (cancelBtn && modal) {
        cancelBtn.addEventListener('click', () => modal.classList.add('hidden'));
    }

    // Register Form Submit
    const form = document.getElementById('registerDonorForm');
    if (form) {
        form.addEventListener('submit', handleRegisterDonor);
    }

    // Turn-by-Turn Directions Drawer Toggle
    const toggleDirBtn = document.getElementById('toggleDirectionsBtn');
    const directionsList = document.getElementById('directionsList');
    if (toggleDirBtn && directionsList) {
        toggleDirBtn.addEventListener('click', () => {
            const isClosed = directionsList.classList.contains('hidden');
            if (isClosed) {
                directionsList.classList.remove('hidden');
                toggleDirBtn.classList.add('open');
            } else {
                directionsList.classList.add('hidden');
                toggleDirBtn.classList.remove('open');
            }
        });
    }
}

/**
 * Triggers Dijkstra Matching via Java REST API
 */
async function handleEmergencySearch() {
    const requesterNodeId = parseInt(document.getElementById('requesterSelect').value, 10);
    const bloodSelect = document.getElementById('bloodTypeSelect');
    const bloodType = bloodSelect ? bloodSelect.value : document.getElementById('selectedBloodType').value;
    const isSimEnabled = document.getElementById('simToggle').checked;

    const findBtn = document.getElementById('findDonorBtn');
    const btnText = document.getElementById('findDonorBtnText');
    findBtn.disabled = true;
    if (btnText) btnText.textContent = 'Computing Dijkstra Paths...';

    // Clear previous routes and simulation layers
    clearActiveRouteLayers();
    clearSimulationLayers();

    // Hide or reset previous match card during calculation to avoid confusion
    const heroCard = document.getElementById('nearestDonorCard');
    if (heroCard) heroCard.classList.add('hidden');

    try {
        const response = await fetch('/api/match', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ requesterNodeId, bloodType })
        });

        const data = await response.json();
        currentMatches = data.matches || [];

        if (isSimEnabled && data.simulationSteps && data.simulationSteps.length > 0) {
            runSimulation(data.simulationSteps, currentMatches, requesterNodeId);
        } else {
            renderMatchResults(currentMatches, requesterNodeId);
        }
    } catch (err) {
        alert('Failed to execute donor search: ' + err.message);
    } finally {
        findBtn.disabled = false;
        if (btnText) btnText.textContent = 'Find Compatible Donors';
    }
}

/**
 * Executes the visual step-by-step Dijkstra simulation with progress bar
 */
function runSimulation(steps, matches, requesterNodeId) {
    const simHud = document.getElementById('simHud');
    const simBadge = document.getElementById('simBadge');
    const simDescText = document.getElementById('simDescText');
    const progressBar = document.getElementById('simProgressBar');

    simHud.classList.remove('hidden');
    simRunning = true;

    let index = 0;
    const totalSteps = steps.length;

    function step() {
        if (!simRunning || index >= totalSteps) {
            endSimulation(matches, requesterNodeId);
            return;
        }

        const current = steps[index];
        index++;

        // Update HUD text and progress bar
        if (simBadge) simBadge.textContent = current.action.replace('_', ' ');
        if (simDescText) simDescText.textContent = current.description;
        if (progressBar) progressBar.style.width = Math.min(100, Math.round((index / totalSteps) * 100)) + '%';

        // Visual effects on map
        handleSimVisuals(current);

        simTimeoutId = setTimeout(step, simSpeed);
    }

    step();
}

/**
 * Draws temporary visual effects for each Dijkstra exploration event
 */
function handleSimVisuals(step) {
    const nodeU = networkData.nodes.find(n => n.id === step.u);
    const nodeV = step.v !== -1 ? networkData.nodes.find(n => n.id === step.v) : null;
    const simBadge = document.getElementById('simBadge');

    if (step.action === 'EXTRACT_MIN' && nodeU) {
        if (simBadge) {
            simBadge.style.borderColor = '#FACC15';
            simBadge.style.color = '#FACC15';
            simBadge.style.backgroundColor = 'rgba(250, 204, 21, 0.15)';
        }

        // Pulse ring on extracted node
        const pulseCircle = L.circleMarker([nodeU.lat, nodeU.lng], {
            radius: 16,
            color: '#FACC15',
            fillColor: '#FACC15',
            fillOpacity: 0.35,
            weight: 2.5
        }).addTo(map);
        simLayers.push(pulseCircle);

    } else if (step.action === 'EXAMINE_EDGE' && nodeU && nodeV) {
        if (simBadge) {
            simBadge.style.borderColor = '#38BDF8';
            simBadge.style.color = '#38BDF8';
            simBadge.style.backgroundColor = 'rgba(56, 189, 248, 0.15)';
        }

        // Draw exploring beam
        const edgeLine = L.polyline([
            [nodeU.lat, nodeU.lng],
            [nodeV.lat, nodeV.lng]
        ], {
            color: '#38BDF8',
            weight: 3.5,
            opacity: 0.8,
            dashArray: '3, 6'
        }).addTo(map);
        simLayers.push(edgeLine);

    } else if (step.action === 'RELAX_EDGE' && nodeU && nodeV) {
        if (simBadge) {
            simBadge.style.borderColor = '#10B981';
            simBadge.style.color = '#10B981';
            simBadge.style.backgroundColor = 'rgba(16, 185, 129, 0.15)';
        }

        // Relaxed road
        const relaxedLine = L.polyline([
            [nodeU.lat, nodeU.lng],
            [nodeV.lat, nodeV.lng]
        ], {
            color: '#10B981',
            weight: 4.5,
            opacity: 0.9
        }).addTo(map);
        simLayers.push(relaxedLine);

        // Target vertex flash
        const targetCircle = L.circleMarker([nodeV.lat, nodeV.lng], {
            radius: 12,
            color: '#10B981',
            fillColor: '#10B981',
            fillOpacity: 0.5,
            weight: 2
        }).addTo(map);
        simLayers.push(targetCircle);

    } else if (step.action === 'SETTLE_NODE' && nodeU) {
        if (simBadge) {
            simBadge.style.borderColor = '#A855F7';
            simBadge.style.color = '#A855F7';
            simBadge.style.backgroundColor = 'rgba(168, 85, 247, 0.15)';
        }

        const settledMarker = L.circleMarker([nodeU.lat, nodeU.lng], {
            radius: 10,
            color: '#A855F7',
            fillColor: '#A855F7',
            fillOpacity: 0.2,
            weight: 2
        }).addTo(map);
        simLayers.push(settledMarker);
    }
}

/**
 * Concludes the simulation and reveals the final optimal shortest path
 */
function endSimulation(matches, requesterNodeId) {
    simRunning = false;
    clearSimulationLayers();
    document.getElementById('simHud').classList.add('hidden');
    renderMatchResults(matches, requesterNodeId);
}

function clearSimulationLayers() {
    simLayers.forEach(l => map.removeLayer(l));
    simLayers = [];
}

/**
 * Clears active road routing layers (GeoJSON casing, primary line, or graph fallback)
 */
function clearActiveRouteLayers() {
    if (activeRoadRouteCasing) {
        map.removeLayer(activeRoadRouteCasing);
        activeRoadRouteCasing = null;
    }
    if (activeRoadRouteLayer) {
        map.removeLayer(activeRoadRouteLayer);
        activeRoadRouteLayer = null;
    }
    if (routePolyline) {
        map.removeLayer(routePolyline);
        routePolyline = null;
    }
}

/**
 * Renders ranked donor list and illuminates shortest road route on the real map
 */
function renderMatchResults(matches, requesterNodeId) {
    const heroCard = document.getElementById('nearestDonorCard');
    const donorListContainer = document.getElementById('rankedDonorList');
    const countBadge = document.getElementById('matchCount');

    if (countBadge) countBadge.textContent = matches.length;

    if (!matches || matches.length === 0) {
        if (heroCard) heroCard.classList.add('hidden');
        donorListContainer.innerHTML = `
            <div class="empty-state-card">
                <p class="empty-title">No Compatible Donors Available</p>
                <p class="empty-desc">No registered donors matching the requested blood type are currently reachable in this division.</p>
            </div>
        `;
        return;
    }

    // 1. Featured Top Nearest Match
    const topMatch = matches[0];
    selectedDonorId = topMatch.donor.id;
    displayFeaturedDonor(topMatch, requesterNodeId);

    // 2. Render Ranked List of Compatible Donors (Strictly ordered by Dijkstra distance)
    donorListContainer.innerHTML = '';
    matches.forEach((m, idx) => {
        const item = document.createElement('div');
        item.className = 'donor-card-item' + (idx === 0 ? ' active' : '');
        item.dataset.donorId = m.donor.id;

        const distText = m.roadDistanceKm === 0.0 ? 'On-site' : `${m.roadDistanceKm.toFixed(2)} km`;
        const etaText = m.estimatedTimeMinutes <= 0.0 ? '< 1m transit' : `~${Math.round(m.estimatedTimeMinutes)}m transit`;

        item.innerHTML = `
            <div class="donor-left-wrap">
                <span class="rank-index">#${idx + 1}</span>
                <span class="donor-type-chip">${m.donor.bloodType}</span>
                <div class="donor-info-block">
                    <span class="donor-card-name">${escapeHtml(m.donor.name)}</span>
                    <div class="donor-card-sub">
                        <span>${escapeHtml(m.donor.area)}</span>
                        <span>•</span>
                        <span class="donor-card-phone">${escapeHtml(m.donor.phone)}</span>
                    </div>
                </div>
            </div>
            <div class="donor-right-wrap">
                <span class="distance-pill">${distText}</span>
                <span class="eta-label">${etaText}</span>
            </div>
        `;

        item.addEventListener('click', () => {
            document.querySelectorAll('.donor-card-item').forEach(el => el.classList.remove('active'));
            item.classList.add('active');
            selectedDonorId = m.donor.id;
            displayFeaturedDonor(m, requesterNodeId);
        });

        donorListContainer.appendChild(item);
    });
}

/**
 * Updates the Primary Responder Hero Card with a specific match's details
 */
function displayFeaturedDonor(match, requesterNodeId) {
    const heroCard = document.getElementById('nearestDonorCard');
    if (!heroCard) return;

    heroCard.classList.remove('hidden');

    // Populate Fields
    document.getElementById('heroBloodGroup').textContent = match.donor.bloodType;
    document.getElementById('heroDonorName').textContent = match.donor.name;
    document.getElementById('heroLocationArea').textContent = `${match.donor.area} (${match.donor.division || 'Dhaka'})`;

    // Direct Phone Callout
    const phoneLink = document.getElementById('heroPhoneLink');
    const phoneText = document.getElementById('heroPhoneText');
    if (phoneLink && phoneText) {
        phoneLink.href = `tel:${match.donor.phone}`;
        phoneText.textContent = match.donor.phone;
    }

    // Secondary DSA Graph Comparison Pill
    const dsaText = document.getElementById('heroDsaDistanceText');
    if (dsaText) {
        dsaText.textContent = match.roadDistanceKm === 0.0
            ? 'Graph Distance: 0.00 km (Facility On-site)'
            : `Graph Distance: ${match.roadDistanceKm.toFixed(2)} km (via Min-Heap)`;
    }

    // Build Dijkstra Node Sequence Breadcrumbs
    const pathContainer = document.getElementById('heroPathSequence');
    if (pathContainer) {
        if (!match.routePath || match.routePath.length <= 1) {
            pathContainer.innerHTML = `<span class="crumb-step">Facility On-site Responder (No Transit Required)</span>`;
        } else {
            const stepNames = match.routePath.map(nodeId => {
                const node = networkData.nodes.find(n => n.id === nodeId);
                return node ? escapeHtml(node.name) : `Node ${nodeId}`;
            });
            pathContainer.innerHTML = stepNames.map((name) => `<span class="crumb-step">${name}</span>`).join('<span class="crumb-arrow">&rarr;</span>');
        }
    }

    // Request & Render Actual Real Road Route via OSRM
    fetchAndRenderRealRoadRoute(requesterNodeId, match);
}

/**
 * Fetches real street geometry from OSRM via backend /api/route and renders high-visibility emerald line
 */
async function fetchAndRenderRealRoadRoute(requesterNodeId, match) {
    // 1. Cancel any prior in-flight request to prevent race conditions on rapid switching
    if (activeRouteAbortController) {
        activeRouteAbortController.abort();
    }
    activeRouteAbortController = new AbortController();
    const signal = activeRouteAbortController.signal;

    // 2. Clear old layers and highlight active donor pin
    clearActiveRouteLayers();
    document.querySelectorAll('.donor-fb-pin').forEach(el => el.classList.remove('selected-highlight'));
    const donorPin = document.getElementById(`donor-pin-${match.donor.id}`);
    if (donorPin) donorPin.classList.add('selected-highlight');

    // Find hospital and donor coordinate nodes
    const reqNode = networkData.nodes.find(n => n.id === requesterNodeId);
    const donorNode = networkData.nodes.find(n => n.id === match.donor.nodeId);

    const distEl = document.getElementById('heroDistance');
    const distSub = document.getElementById('heroDistSub');
    const distLabel = document.getElementById('heroDistLabel');
    const etaEl = document.getElementById('heroEta');
    const etaSub = document.getElementById('heroEtaSub');
    const tagEl = document.getElementById('heroRoutingTag');
    const providerBadge = document.getElementById('heroProviderBadge');
    const providerText = document.getElementById('heroProviderText');
    const directionsDrawer = document.getElementById('directionsDrawer');

    // Case A: On-site donor at the same facility
    if (!donorNode || !reqNode || requesterNodeId === match.donor.nodeId) {
        if (distLabel) distLabel.textContent = 'Road Distance';
        if (distEl) distEl.textContent = 'On-site';
        if (distSub) distSub.textContent = 'Facility on-site responder';
        if (etaEl) etaEl.textContent = '< 1 min';
        if (etaSub) etaSub.textContent = 'Immediate availability';
        if (tagEl) tagEl.textContent = 'On-site Responder';
        if (providerBadge) providerBadge.className = 'provider-pill';
        if (providerText) providerText.textContent = 'Internal Facility';
        if (directionsDrawer) directionsDrawer.classList.add('hidden');
        const onSiteTag = document.getElementById('techSummaryTag');
        if (onSiteTag) onSiteTag.textContent = 'On-site Facility';

        if (donorNode) {
            map.flyTo([donorNode.lat, donorNode.lng], 15, { duration: 0.6 });
        }
        return;
    }

    // Set temporary pending calculation state
    if (distLabel) distLabel.textContent = 'OSRM Road Distance';
    if (distEl) distEl.textContent = 'Calculating...';
    if (distSub) distSub.textContent = 'Querying OpenStreetMap network...';
    if (etaEl) etaEl.textContent = '...';
    if (tagEl) tagEl.textContent = 'Computing OSRM Route...';
    if (providerBadge) providerBadge.className = 'provider-pill';
    if (providerText) providerText.textContent = 'OSRM Network';

    try {
        const url = `/api/route?startLat=${reqNode.lat}&startLon=${reqNode.lng}&endLat=${donorNode.lat}&endLon=${donorNode.lng}`;
        const res = await fetch(url, { signal });
        const data = await res.json();

        if (data.status === 'success' && data.raw && data.raw.routes && data.raw.routes.length > 0) {
            const osrmRoute = data.raw.routes[0];

            // Render high-visibility emerald road line with casing
            activeRoadRouteCasing = L.geoJSON(osrmRoute.geometry, {
                style: {
                    color: '#064E3B',
                    weight: 8,
                    opacity: 0.7,
                    lineCap: 'round',
                    lineJoin: 'round'
                }
            }).addTo(map);

            activeRoadRouteLayer = L.geoJSON(osrmRoute.geometry, {
                style: {
                    color: '#10B981',
                    weight: 5,
                    opacity: 0.98,
                    lineCap: 'round',
                    lineJoin: 'round',
                    className: 'active-road-route'
                }
            }).addTo(map);

            // Fit map view to exact route bounds
            map.fitBounds(activeRoadRouteLayer.getBounds(), {
                padding: [50, 50],
                maxZoom: 16
            });

            // Update UI Metrics
            if (distLabel) distLabel.textContent = 'OSRM Road Distance';
            if (distEl) distEl.textContent = `${data.distanceKm.toFixed(2)} km`;
            if (distSub) distSub.textContent = 'Actual street distance';
            if (etaEl) etaEl.textContent = data.durationMinutes < 1 ? '< 1 min' : `~${Math.round(data.durationMinutes)} mins`;
            if (etaSub) etaSub.textContent = 'Live road transit duration';
            if (tagEl) tagEl.textContent = 'OSRM Real Road Network';
            if (providerBadge) providerBadge.className = 'provider-pill';
            if (providerText) providerText.textContent = 'OSRM Live Navigation';

            const techTag = document.getElementById('techSummaryTag');
            if (techTag) {
                const stepCount = (osrmRoute.legs && osrmRoute.legs[0] && osrmRoute.legs[0].steps) ? osrmRoute.legs[0].steps.length : 0;
                techTag.textContent = `OSRM Road Route • ${stepCount} steps`;
            }

            // Populate Turn-by-Turn Navigation Steps
            if (osrmRoute.legs && osrmRoute.legs[0] && osrmRoute.legs[0].steps && osrmRoute.legs[0].steps.length > 0) {
                renderTurnByTurnDirections(osrmRoute.legs[0].steps);
                if (directionsDrawer) directionsDrawer.classList.remove('hidden');
            } else if (directionsDrawer) {
                directionsDrawer.classList.add('hidden');
            }
        } else {
            // OSRM could not find road path — fall back to Dijkstra graph line
            renderDijkstraFallback(match, reqNode, donorNode);
        }
    } catch (err) {
        if (err.name === 'AbortError') return; // User selected another donor rapidly
        console.warn('OSRM route fetch failed, using Dijkstra fallback:', err);
        renderDijkstraFallback(match, reqNode, donorNode);
    }
}

/**
 * Fallback renderer when external OSRM service is offline or unreachable
 */
function renderDijkstraFallback(match, reqNode, donorNode) {
    clearActiveRouteLayers();

    const distLabel = document.getElementById('heroDistLabel');
    const distEl = document.getElementById('heroDistance');
    const distSub = document.getElementById('heroDistSub');
    const etaEl = document.getElementById('heroEta');
    const etaSub = document.getElementById('heroEtaSub');
    const tagEl = document.getElementById('heroRoutingTag');
    const providerBadge = document.getElementById('heroProviderBadge');
    const providerText = document.getElementById('heroProviderText');
    const directionsDrawer = document.getElementById('directionsDrawer');

    if (distLabel) distLabel.textContent = 'Graph Road Distance';
    if (distEl) distEl.textContent = `${match.roadDistanceKm.toFixed(2)} km`;
    if (distSub) distSub.textContent = 'Dijkstra graph topology';
    if (etaEl) etaEl.textContent = `~${Math.round(match.estimatedTimeMinutes)} mins`;
    if (etaSub) etaSub.textContent = 'Computed graph transit';
    if (tagEl) tagEl.textContent = 'Dijkstra Min-Heap Metric';
    if (providerBadge) providerBadge.className = 'provider-pill fallback';
    if (providerText) providerText.textContent = 'Graph Metric (OSRM Offline)';
    if (directionsDrawer) directionsDrawer.classList.add('hidden');

    const latLngs = [];
    (match.routePath || []).forEach(id => {
        const node = networkData.nodes.find(n => n.id === id);
        if (node) latLngs.push([node.lat, node.lng]);
    });

    if (latLngs.length > 1) {
        routePolyline = L.polyline(latLngs, {
            color: '#F59E0B',
            weight: 4.5,
            opacity: 0.9,
            dashArray: '6, 8',
            lineJoin: 'round',
            lineCap: 'round'
        }).addTo(map);

        map.fitBounds(routePolyline.getBounds(), {
            padding: [50, 50],
            maxZoom: 16
        });
    }
}

/**
 * Renders Turn-by-Turn Driving Steps into collapsible drawer
 */
function renderTurnByTurnDirections(steps) {
    const listEl = document.getElementById('directionsList');
    const badgeEl = document.getElementById('directionsStepBadge');
    if (!listEl) return;

    if (badgeEl) badgeEl.textContent = `${steps.length} steps`;
    listEl.innerHTML = '';

    const totalSteps = steps.length;
    steps.forEach((step, idx) => {
        const item = document.createElement('div');
        item.className = 'direction-step-item';

        const mType = (step.maneuver && step.maneuver.type) || 'turn';
        const mModifier = (step.maneuver && step.maneuver.modifier) || '';
        const instruction = formatStepInstruction(step, idx, totalSteps);

        let distText = '';
        if (step.distance > 0) {
            distText = step.distance >= 1000 ? `${(step.distance / 1000).toFixed(1)} km` : `${Math.round(step.distance)} m`;
        }
        let durText = '';
        if (step.duration > 0) {
            durText = step.duration >= 60 ? `~${Math.round(step.duration / 60)} min` : `< 1 min`;
        }
        const metaStr = [distText, durText].filter(Boolean).join(' • ');

        let iconWrapClass = 'step-icon-wrap';
        if (mType === 'arrive' || idx === totalSteps - 1) iconWrapClass += ' arrive';
        else if (mType === 'depart' || idx === 0) iconWrapClass += ' depart';

        item.innerHTML = `
            <div class="${iconWrapClass}">
                ${getManeuverIcon(mType, mModifier, idx === totalSteps - 1)}
            </div>
            <div class="step-content">
                <span class="step-instruction">${instruction}</span>
                ${metaStr ? `<span class="step-meta">${metaStr}</span>` : ''}
            </div>
        `;

        listEl.appendChild(item);
    });
}

function getManeuverIcon(type, modifier, isLast) {
    if (isLast || type === 'arrive') {
        return `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s-8-4.5-8-11.8A8 8 0 0 1 12 2a8 8 0 0 1 8 8.2c0 7.3-8 11.8-8 11.8z"/><circle cx="12" cy="10" r="3"/></svg>`;
    }
    if (type === 'depart') {
        return `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="7" y1="17" x2="17" y2="7"/><polyline points="7 7 17 7 17 17"/></svg>`;
    }
    if (modifier && (modifier.includes('right'))) {
        return `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/><line x1="4" y1="12" x2="14" y2="12"/></svg>`;
    }
    if (modifier && (modifier.includes('left'))) {
        return `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/><line x1="20" y1="12" x2="10" y2="12"/></svg>`;
    }
    if (type === 'roundabout') {
        return `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>`;
    }
    return `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>`;
}

function formatStepInstruction(step, index, totalSteps) {
    if (index === totalSteps - 1 || (step.maneuver && step.maneuver.type === 'arrive')) {
        return 'Arrive at destination (Donor location)';
    }
    const maneuver = step.maneuver || {};
    const type = maneuver.type || 'turn';
    const modifier = maneuver.modifier || '';
    const name = (step.name && step.name.trim().length > 0) ? step.name.trim() : null;

    if (type === 'depart') {
        return name ? `Depart onto <strong>${escapeHtml(name)}</strong>` : 'Depart and follow road';
    }
    if (type === 'turn') {
        const dir = modifier ? modifier.replace('-', ' ') : 'turn';
        return name ? `Turn ${dir} onto <strong>${escapeHtml(name)}</strong>` : `Turn ${dir}`;
    }
    if (type === 'roundabout') {
        return name ? `Take roundabout exit onto <strong>${escapeHtml(name)}</strong>` : 'Enter roundabout';
    }
    if (modifier) {
        const modStr = modifier.replace('-', ' ');
        return name ? `${modStr.charAt(0).toUpperCase() + modStr.slice(1)} onto <strong>${escapeHtml(name)}</strong>` : `${modStr.charAt(0).toUpperCase() + modStr.slice(1)}`;
    }
    return name ? `Continue onto <strong>${escapeHtml(name)}</strong>` : 'Continue straight';
}

/**
 * Handles Donor Registration form submission
 */
async function handleRegisterDonor(e) {
    e.preventDefault();

    const name = document.getElementById('regName').value.trim();
    const bloodType = document.getElementById('regBloodType').value;
    const phone = document.getElementById('regPhone').value.trim();
    const locationNodeId = parseInt(document.getElementById('regLocation').value, 10);
    const address = document.getElementById('regAddress').value.trim();

    if (!name || !phone || isNaN(locationNodeId)) {
        alert('Please fill out all required fields.');
        return;
    }

    const donorPayload = {
        name,
        bloodType,
        phone,
        locationNodeId,
        address
    };

    try {
        const res = await fetch('/api/donors', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(donorPayload)
        });

        const result = await res.json();

        if (res.ok && result.success) {
            // Close modal
            document.getElementById('registerModal').classList.add('hidden');
            document.getElementById('registerDonorForm').reset();

            // Refresh donor list from server
            const donorRes = await fetch('/api/donors');
            donorsData = await donorRes.json();
            renderDonorsOnMap(donorsData);

            alert(`Donor successfully enrolled in the emergency registry! Assigned ID: ${result.donor.id}`);
        } else {
            alert('Failed to register donor: ' + (result.error || 'Unknown error'));
        }
    } catch (err) {
        alert('Network error registering donor: ' + err.message);
    }
}
