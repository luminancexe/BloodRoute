/**
 * BloodRoute: Frontend Map & Emergency Dispatcher Controller
 * Interacts with pure Java REST endpoints on localhost.
 * Features real-time step-by-step Dijkstra simulation visualization.
 */

let map;
let networkData = { nodes: [], edges: [] };
let donorsData = [];
let routePolyline = null;
let nodeMarkers = {};
let donorMarkers = [];

// Simulation State
let simRunning = false;
let simSpeed = 250; // ms per step
let simTimeoutId = null;
let simLayers = []; // temporary visual layers (active nodes, exploring edges)

// Initialize application on DOM load
document.addEventListener('DOMContentLoaded', () => {
    initMap();
    setupEventListeners();
    loadNetworkAndDonors();
});

/**
 * Initializes the Leaflet Real Map centered over Dhaka
 */
function initMap() {
    // Dhaka City Center: 23.765, 90.39
    map = L.map('map', {
        zoomControl: true
    }).setView([23.765, 90.39], 13);

    // High quality OpenStreetMap tiles
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors | BloodRoute DSA'
    }).addTo(map);
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

        populateDropdowns();
        renderNetworkGraph();
        renderDonorsOnMap(donorsData);
    } catch (err) {
        console.error('Error fetching data from BloodRoute Java server:', err);
    }
}

/**
 * Populates location selectors in UI
 */
function populateDropdowns() {
    const requesterSelect = document.getElementById('requesterSelect');
    const regLocationSelect = document.getElementById('regLocation');

    requesterSelect.innerHTML = '';
    regLocationSelect.innerHTML = '';

    networkData.nodes.forEach(node => {
        const option = document.createElement('option');
        option.value = node.id;
        option.textContent = `${node.name} (${node.area})${node.isHospital ? ' [Hospital]' : ''}`;
        
        // Default requester to Dhaka Medical College (Node 0)
        if (node.id === 0) option.selected = true;
        requesterSelect.appendChild(option);

        const regOption = option.cloneNode(true);
        regLocationSelect.appendChild(regOption);
    });
}

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
                color: '#64748b',
                weight: 3,
                opacity: 0.5,
                dashArray: '4, 6'
            }).addTo(map);
        }
    });

    // 2. Draw Nodes (Intersections & Hospitals)
    networkData.nodes.forEach(node => {
        const isHosp = node.isHospital;
        const iconHtml = `<div style="
            background: ${isHosp ? '#2563eb' : '#475569'};
            color: white;
            border-radius: 50%;
            width: ${isHosp ? '28px' : '18px'};
            height: ${isHosp ? '28px' : '18px'};
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: ${isHosp ? '14px' : '10px'};
            border: 2px solid white;
            box-shadow: 0 2px 6px rgba(0,0,0,0.3);
        ">${isHosp ? '🏥' : '📍'}</div>`;

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'custom-node-icon',
            iconSize: isHosp ? [28, 28] : [18, 18],
            iconAnchor: isHosp ? [14, 14] : [9, 9]
        });

        const marker = L.marker([node.lat, node.lng], { icon: customIcon }).addTo(map);
        marker.bindPopup(`<strong>${node.name}</strong><br>Area: ${node.area}<br>Type: ${isHosp ? 'Hospital' : 'Intersection'}`);
        nodeMarkers[node.id] = marker;
    });
}

/**
 * Renders donor markers across Dhaka with blood badges
 */
function renderDonorsOnMap(donors) {
    donorMarkers.forEach(m => map.removeLayer(m));
    donorMarkers = [];

    donors.forEach(donor => {
        const node = networkData.nodes.find(n => n.id === donor.nodeId);
        if (!node) return;

        const offsetLat = (Math.random() - 0.5) * 0.002;
        const offsetLng = (Math.random() - 0.5) * 0.002;

        const iconHtml = `<div style="
            background: ${donor.isAvailable ? '#dc2626' : '#6b7280'};
            color: white;
            font-weight: 800;
            font-size: 11px;
            padding: 2px 6px;
            border-radius: 12px;
            border: 2px solid white;
            box-shadow: 0 2px 8px rgba(220,38,38,0.5);
            display: flex;
            align-items: center;
            gap: 2px;
            white-space: nowrap;
        ">🩸 ${donor.bloodType}</div>`;

        const donorIcon = L.divIcon({
            html: iconHtml,
            className: 'custom-donor-icon',
            iconSize: [45, 20],
            iconAnchor: [22, 10]
        });

        const marker = L.marker([node.lat + offsetLat, node.lng + offsetLng], { icon: donorIcon }).addTo(map);
        marker.bindPopup(`
            <strong>${donor.name}</strong><br>
            Blood Group: <strong>${donor.bloodType}</strong><br>
            Status: ${donor.isAvailable ? '🟢 Available' : '🔴 Busy'}<br>
            Phone: <a href="tel:${donor.phone}">${donor.phone}</a><br>
            Location: ${donor.area}
        `);
        donorMarkers.push(marker);
    });
}

/**
 * Sets up button clicks, modal controls, and speed buttons
 */
function setupEventListeners() {
    // Blood Group Selector Buttons
    const bloodButtons = document.querySelectorAll('.blood-btn');
    bloodButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            bloodButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById('selectedBloodType').value = btn.dataset.type;
        });
    });

    // Find Nearest Compatible Donor CTA
    document.getElementById('findDonorBtn').addEventListener('click', handleEmergencySearch);

    // Register Modal
    const modal = document.getElementById('registerModal');
    document.getElementById('openRegisterModalBtn').addEventListener('click', () => modal.classList.remove('hidden'));
    document.getElementById('closeRegisterModalBtn').addEventListener('click', () => modal.classList.add('hidden'));

    // Register Form Submit
    document.getElementById('registerDonorForm').addEventListener('submit', handleRegisterDonor);

    // Simulation Speed Control Buttons
    const speedButtons = document.querySelectorAll('.speed-btn');
    speedButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            speedButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            simSpeed = parseInt(btn.dataset.speed, 10);
        });
    });

    // Skip Simulation
    document.getElementById('simSkipBtn').addEventListener('click', () => {
        simRunning = false;
        if (simTimeoutId) clearTimeout(simTimeoutId);
    });
}

/**
 * Triggers Dijkstra Matching via Java REST API
 */
async function handleEmergencySearch() {
    const requesterNodeId = parseInt(document.getElementById('requesterSelect').value, 10);
    const bloodType = document.getElementById('selectedBloodType').value;
    const isSimEnabled = document.getElementById('simToggle').checked;

    const findBtn = document.getElementById('findDonorBtn');
    findBtn.disabled = true;
    findBtn.textContent = '⏳ Executing Dijkstra Pathfinding...';

    // Clear previous route
    if (routePolyline) {
        map.removeLayer(routePolyline);
        routePolyline = null;
    }
    clearSimulationLayers();

    try {
        const response = await fetch('/api/match', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ requesterNodeId, bloodType })
        });

        const data = await response.json();

        if (isSimEnabled && data.simulationSteps && data.simulationSteps.length > 0) {
            runSimulation(data.simulationSteps, data.matches, requesterNodeId);
        } else {
            renderMatchResults(data.matches, requesterNodeId);
        }
    } catch (err) {
        alert('Failed to execute donor search: ' + err.message);
    } finally {
        findBtn.disabled = false;
        findBtn.textContent = '⚡ Find Nearest Compatible Donor';
    }
}

/**
 * Executes the visual step-by-step Dijkstra simulation on OpenStreetMap
 */
function runSimulation(steps, matches, requesterNodeId) {
    const simHud = document.getElementById('simHud');
    const simBadge = document.getElementById('simBadge');
    const simStepText = document.getElementById('simStepText');
    const simDescText = document.getElementById('simDescText');

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

        // Update HUD
        simStepText.textContent = `Step ${index} / ${totalSteps}`;
        simBadge.textContent = current.action.replace('_', ' ');
        simDescText.textContent = current.description;

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

    if (step.action === 'EXTRACT_MIN' && nodeU) {
        simBadge.style.borderColor = '#facc15';
        simBadge.style.color = '#facc15';

        // Draw pulsing gold ring on extracted node
        const pulseCircle = L.circleMarker([nodeU.lat, nodeU.lng], {
            radius: 18,
            color: '#facc15',
            fillColor: '#facc15',
            fillOpacity: 0.35,
            weight: 3
        }).addTo(map);
        simLayers.push(pulseCircle);

    } else if (step.action === 'EXAMINE_EDGE' && nodeU && nodeV) {
        simBadge.style.borderColor = '#38bdf8';
        simBadge.style.color = '#38bdf8';

        // Draw exploring beam between u and v
        const edgeLine = L.polyline([
            [nodeU.lat, nodeU.lng],
            [nodeV.lat, nodeV.lng]
        ], {
            color: '#38bdf8',
            weight: 4,
            opacity: 0.8,
            dashArray: '3, 6'
        }).addTo(map);
        simLayers.push(edgeLine);

    } else if (step.action === 'RELAX_EDGE' && nodeU && nodeV) {
        simBadge.style.borderColor = '#22c55e';
        simBadge.style.color = '#22c55e';

        // Flash relaxed road in vibrant green
        const relaxedLine = L.polyline([
            [nodeU.lat, nodeU.lng],
            [nodeV.lat, nodeV.lng]
        ], {
            color: '#22c55e',
            weight: 5,
            opacity: 0.9
        }).addTo(map);
        simLayers.push(relaxedLine);

        // Flash target node
        const targetCircle = L.circleMarker([nodeV.lat, nodeV.lng], {
            radius: 14,
            color: '#22c55e',
            fillColor: '#22c55e',
            fillOpacity: 0.5,
            weight: 2
        }).addTo(map);
        simLayers.push(targetCircle);

    } else if (step.action === 'SETTLE_NODE' && nodeU) {
        simBadge.style.borderColor = '#a855f7';
        simBadge.style.color = '#a855f7';

        const settledMarker = L.circleMarker([nodeU.lat, nodeU.lng], {
            radius: 12,
            color: '#a855f7',
            fillColor: '#a855f7',
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
 * Renders ranked donor list and illuminates shortest route on the real map
 */
function renderMatchResults(matches, requesterNodeId) {
    const nearestCard = document.getElementById('nearestDonorCard');
    const rankedList = document.getElementById('rankedDonorList');
    const matchCount = document.getElementById('matchCount');

    rankedList.innerHTML = '';
    matchCount.textContent = matches.length;

    if (!matches || matches.length === 0) {
        nearestCard.classList.add('hidden');
        rankedList.innerHTML = '<p class="placeholder-text">⚠️ No available compatible donors found in registry.</p>';
        if (routePolyline) map.removeLayer(routePolyline);
        return;
    }

    // Top match (Nearest)
    const topMatch = matches[0];
    nearestCard.classList.remove('hidden');
    document.getElementById('heroBloodGroup').textContent = topMatch.donor.bloodType;
    document.getElementById('heroDonorName').textContent = topMatch.donor.name;
    document.getElementById('heroLocationArea').textContent = `📍 ${topMatch.donor.area} (${topMatch.donor.address})`;
    document.getElementById('heroDistance').textContent = `${topMatch.roadDistanceKm.toFixed(2)} km`;
    document.getElementById('heroEta').textContent = `${topMatch.estimatedTimeMinutes.toFixed(1)} mins`;
    
    const phoneEl = document.getElementById('heroPhone');
    phoneEl.textContent = topMatch.donor.phone;
    phoneEl.href = `tel:${topMatch.donor.phone}`;

    // Path sequence text
    const pathNames = topMatch.routePath.map(id => {
        const n = networkData.nodes.find(node => node.id === id);
        return n ? n.name.split('(')[0].trim() : `Node ${id}`;
    });
    document.getElementById('heroPathSequence').innerHTML = pathNames.join(' ➔ ');

    // Highlight route on the real map
    highlightRouteOnMap(topMatch.routePath);

    // Populate Ranked List
    matches.forEach((m, index) => {
        const item = document.createElement('div');
        item.className = 'donor-item';
        item.innerHTML = `
            <div class="donor-item-left">
                <span class="blood-pill">${m.donor.bloodType}</span>
                <div>
                    <div class="donor-name-text">#${index + 1} ${m.donor.name}</div>
                    <div class="donor-area-text">${m.donor.area} &bull; ${m.donor.phone}</div>
                </div>
            </div>
            <div class="donor-dist-text">
                ${m.isReachable ? m.roadDistanceKm.toFixed(2) + ' km' : 'Unreachable'}
            </div>
        `;
        item.addEventListener('click', () => {
            highlightRouteOnMap(m.routePath);
        });
        rankedList.appendChild(item);
    });
}

/**
 * Draws the Dijkstra shortest route polyline on OpenStreetMap
 */
function highlightRouteOnMap(pathNodeIds) {
    if (routePolyline) {
        map.removeLayer(routePolyline);
    }

    if (!pathNodeIds || pathNodeIds.length < 2) return;

    const latLngs = pathNodeIds.map(id => {
        const node = networkData.nodes.find(n => n.id === id);
        return [node.lat, node.lng];
    });

    // Glowing emergency red route line
    routePolyline = L.polyline(latLngs, {
        color: '#dc2626',
        weight: 6,
        opacity: 0.9,
        lineCap: 'round',
        lineJoin: 'round'
    }).addTo(map);

    // Zoom map smoothly to encompass the full emergency route
    map.fitBounds(routePolyline.getBounds(), { padding: [50, 50] });
}

/**
 * Handles registering new donor
 */
async function handleRegisterDonor(e) {
    e.preventDefault();
    const payload = {
        name: document.getElementById('regName').value,
        bloodType: document.getElementById('regBloodType').value,
        locationNodeId: parseInt(document.getElementById('regLocation').value, 10),
        phoneNumber: document.getElementById('regPhone').value,
        address: document.getElementById('regAddress').value,
        isAvailable: true
    };

    try {
        const res = await fetch('/api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json();
        if (res.ok) {
            alert('✅ Donor registered successfully!');
            document.getElementById('registerModal').classList.add('hidden');
            document.getElementById('registerDonorForm').reset();
            loadNetworkAndDonors(); // Refresh donor list
        } else {
            alert('❌ Registration failed: ' + data.error);
        }
    } catch (err) {
        alert('❌ Error: ' + err.message);
    }
}
