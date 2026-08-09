// Project Table Page JavaScript

let dataTable; // Global reference to DataTable instance
let allProjects = []; // Store all projects data

document.addEventListener("DOMContentLoaded", function () {
    const token = localStorage.getItem("jwtToken");

    if (!token) {
        window.location.href = "/login"; // Redirect if not logged in
        return;
    }

    // Initialize filter button handlers
    initializeFilterButtons();

    // Drop focus before Bootstrap marks a modal aria-hidden, otherwise the browser
    // warns that a focused descendant (e.g. .btn-close) is hidden from assistive tech.
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('hide.bs.modal', function () {
            if (modal.contains(document.activeElement)) {
                document.activeElement.blur();
            }
        });
    });

    fetchAndPopulateTable(token);
});

// Fetch data and populate table
function fetchAndPopulateTable(token, statusFilter = 'ALL') {
    fetch('/projects/table-data', {
        method: "GET",
        headers: {
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json"
        }
    })
    .then(response => {
        if (response.status === 401) {
            alert("Session expired. Please log in again.");
            localStorage.removeItem('jwtToken');
            window.location.href = "/login";
            return;
        }
        return response.json();
    })
    .then(data => {
        if (!Array.isArray(data)) {
            console.error("Invalid data format:", data);
            return;
        }

        // Store all projects
        allProjects = data;

        // Filter projects based on status
        let filteredProjects = statusFilter === 'ALL'
            ? data
            : data.filter(project => project.status === statusFilter);

        populateTable(filteredProjects);
    })
    .catch(error => console.error('Error fetching projects:', error));
}

// Populate table with filtered data
function populateTable(projects) {
    let tableBody = document.querySelector("#example tbody");

    if (!tableBody) {
        console.error("Table body not found!");
        return;
    }

    // Destroy existing DataTable if it exists
    if ($.fn.DataTable.isDataTable('#example')) {
        $('#example').DataTable().clear().destroy();
    }

    tableBody.innerHTML = ""; // Clear table before adding new data

    projects.forEach(project => {
        let row = document.createElement("tr");


        row.innerHTML = `
            <td>${project.projectIdentifier}</td>
            <td>
                <select class="form-select status-select ${getStatusSelectClass(project.status)}"
                        data-project-id="${project.projectIdentifier}"
                        data-current-status="${project.status}">
                    <option value="ACTIVE" ${project.status === 'ACTIVE' ? 'selected' : ''}>ACTIVE</option>
                    <option value="INACTIVE" ${project.status === 'INACTIVE' ? 'selected' : ''}>INACTIVE</option>
                    <option value="CLOSED" ${project.status === 'CLOSED' ? 'selected' : ''}>CLOSED</option>
                    <option value="INVOICED" ${project.status === 'INVOICED' ? 'selected' : ''}>INVOICED</option>
                </select>
            </td>
            <td>${project.complete}</td>
            <td>${project.terminate}</td>
            <td>${project.quotafull}</td>
            <td>${project.securityTerminate}</td>
            <td>
                <div class="d-flex align-items-center">
                    <input type="number"
                           class="form-control form-control-sm counts-input"
                           value="${project.counts || 0}"
                           data-project-id="${project.projectIdentifier}"
                           data-original-value="${project.counts || 0}"
                           min="0"
                           style="width: 80px; margin-right: 5px;">
                    <button type="button"
                            class="btn btn-sm btn-primary save-counts"
                            data-project-id="${project.projectIdentifier}"
                            title="Save Counts">
                        <i class="fas fa-save"></i>
                    </button>
                </div>
            </td>
            <td>
                <button type="button"
                        class="btn btn-success view-vendors"
                        data-project-id="${project.projectIdentifier}">View Vendors</button>
            </td>
            <td>
                <button type="button"
                        class="btn btn-info view-details"
                        data-project-id="${project.projectIdentifier}"
                        data-loi="${project.loi || 'N/A'}"
                        data-ir="${project.ir || 'N/A'}"
                        data-quota="${project.quota || 'N/A'}"
                        data-cpi="${project.cpi || 'N/A'}">View Details</button>
            </td>
            <td>
                <button type="button"
                        class="btn btn-sm btn-danger delete-project"
                        data-project-id="${project.projectIdentifier}"
                        title="Delete Project">
                    <i class="fas fa-trash"></i>
                </button>
            </td>
        `;
        tableBody.appendChild(row);
    });

    initializeDataTable();
}

// Initialize filter buttons
function initializeFilterButtons() {
    const filterButtons = document.querySelectorAll('.filter-btn');

    filterButtons.forEach(button => {
        button.addEventListener('click', function() {
            const status = this.getAttribute('data-status');
            const token = localStorage.getItem("jwtToken");

            // Remove active class from all buttons
            filterButtons.forEach(btn => btn.classList.remove('active'));

            // Add active class to clicked button
            this.classList.add('active');

            // Filter and display projects
            if (status === 'ALL') {
                populateTable(allProjects);
            } else {
                const filteredProjects = allProjects.filter(project => project.status === status);
                populateTable(filteredProjects);
            }
        });
    });
}

// Handle status change from dropdown
document.addEventListener('change', function (event) {
    if (event.target.classList.contains('status-select')) {
        const selectElement = event.target;
        const projectId = selectElement.getAttribute('data-project-id');
        const currentStatus = selectElement.getAttribute('data-current-status');
        const newStatus = selectElement.value;

        // If status hasn't actually changed, do nothing
        if (newStatus === currentStatus) {
            return;
        }

        // Confirm status change
        if (!confirm(`Change project ${projectId} status from ${currentStatus} to ${newStatus}?`)) {
            // User cancelled, revert to current status
            selectElement.value = currentStatus;
            return;
        }

        const token = localStorage.getItem("jwtToken");

        // Call API to update status
        fetch(`/projects/status/update/${projectId}?status=${newStatus}`, {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            }
        })
        .then(response => {
            if (response.status === 401) {
                alert("Session expired. Please log in again.");
                localStorage.removeItem('jwtToken');
                window.location.href = "/login";
                return;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.success) {
                // Update was successful
                const updatedStatus = data.status;

                // Update the data attribute with new status
                selectElement.setAttribute('data-current-status', updatedStatus);

                // Update the select element styling based on new status
                selectElement.className = `form-select status-select ${getStatusSelectClass(updatedStatus)}`;

                // Update the project in allProjects array
                const projectIndex = allProjects.findIndex(p => p.projectIdentifier === projectId);
                if (projectIndex !== -1) {
                    allProjects[projectIndex].status = updatedStatus;
                }


                // Refresh the table with current filter
                const activeFilterBtn = document.querySelector('.filter-btn.active');
                const currentFilter = activeFilterBtn ? activeFilterBtn.getAttribute('data-status') : 'ALL';

                if (currentFilter === 'ALL') {
                    populateTable(allProjects);
                } else {
                    const filteredProjects = allProjects.filter(project => project.status === currentFilter);
                    populateTable(filteredProjects);
                }

            } else if (data && data.error) {
                alert("Failed to update status: " + data.error);
                // Revert to previous status
                selectElement.value = currentStatus;
            } else {
                alert("Failed to update status.");
                // Revert to previous status
                selectElement.value = currentStatus;
            }
        })
        .catch(error => {
            console.error("Error updating status:", error);
            alert("Error updating status. Please try again.");
            // Revert to previous status
            selectElement.value = currentStatus;
        });
    }
});

function getStatusSelectClass(status) {
    // Return CSS class based on status
    const statusStr = String(status).trim().toUpperCase();
    switch(statusStr) {
        case 'ACTIVE':
            return 'status-active';
        case 'INACTIVE':
            return 'status-inactive';
        case 'CLOSED':
            return 'status-closed';
        case 'INVOICED':
            return 'status-invoiced';
        default:
            return '';
    }
}

// Modal handlers
document.addEventListener('click', function (event) {
    // Handle Save Counts button
    if (event.target.closest('.save-counts')) {
        const button = event.target.closest('.save-counts');
        const projectId = button.getAttribute('data-project-id');
        const input = document.querySelector(`.counts-input[data-project-id="${projectId}"]`);
        const newCounts = parseInt(input.value);
        const originalValue = parseInt(input.getAttribute('data-original-value'));

        // Validate the input
        if (isNaN(newCounts) || newCounts < 0) {
            alert('Please enter a valid non-negative number for counts.');
            input.value = originalValue; // Reset to original value
            return;
        }

        // If value hasn't changed, do nothing
        if (newCounts === originalValue) {
            return;
        }

        const token = localStorage.getItem("jwtToken");

        // Disable button and input during update
        button.disabled = true;
        input.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

        // Call API to update counts
        fetch(`/projects/counts/update/${projectId}?counts=${newCounts}`, {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            }
        })
        .then(response => {
            if (response.status === 401) {
                alert("Session expired. Please log in again.");
                localStorage.removeItem('jwtToken');
                window.location.href = "/login";
                return;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.success) {
                // Update was successful
                input.setAttribute('data-original-value', data.counts);

                // Update the project in allProjects array
                const projectIndex = allProjects.findIndex(p => p.projectIdentifier === projectId);
                if (projectIndex !== -1) {
                    allProjects[projectIndex].counts = data.counts;
                }

                // Show visual feedback
                button.classList.add('btn-success');
                button.classList.remove('btn-primary');
                button.innerHTML = '<i class="fas fa-check"></i>';

                setTimeout(() => {
                    button.classList.remove('btn-success');
                    button.classList.add('btn-primary');
                    button.innerHTML = '<i class="fas fa-save"></i>';
                    button.disabled = false;
                    input.disabled = false;
                }, 1500);

            } else if (data && data.error) {
                alert("Failed to update counts: " + data.error);
                input.value = originalValue; // Revert to original value
                button.innerHTML = '<i class="fas fa-save"></i>';
                button.disabled = false;
                input.disabled = false;
            } else {
                alert("Failed to update counts.");
                input.value = originalValue;
                button.innerHTML = '<i class="fas fa-save"></i>';
                button.disabled = false;
                input.disabled = false;
            }
        })
        .catch(error => {
            console.error("Error updating counts:", error);
            alert("Error updating counts. Please try again.");
            input.value = originalValue; // Revert to original value
            button.innerHTML = '<i class="fas fa-save"></i>';
            button.disabled = false;
            input.disabled = false;
        });
    }

    // Handle View Details button
    if (event.target.closest('.view-details')) {
        // Read from the button itself: on mobile, DataTables Responsive moves the
        // button into a child row, so the parent <tr> no longer holds the data.
        const button = event.target.closest('.view-details');
        const projectId = button.getAttribute('data-project-id');
        const loi = button.getAttribute('data-loi');
        const ir = button.getAttribute('data-ir');
        const quota = button.getAttribute('data-quota');
        const cpi = button.getAttribute('data-cpi');

        // Populate the details modal
        document.getElementById('detailsProjectId').textContent = projectId;
        document.getElementById('detailsLOI').textContent = loi;
        document.getElementById('detailsIR').textContent = ir;
        document.getElementById('detailsQuota').textContent = quota;
        document.getElementById('detailsCpi').textContent = cpi;

        // Show the modal using Bootstrap's API
        const detailsModal = bootstrap.Modal.getOrCreateInstance(document.getElementById('detailsModal'));
        detailsModal.show();
    }

    // Handle Delete Project button
    if (event.target.closest('.delete-project')) {
        const button = event.target.closest('.delete-project');
        const projectId = button.getAttribute('data-project-id');

        // Confirm deletion
        if (!confirm(`Are you sure you want to delete project "${projectId}"?\n\nThis action cannot be undone!`)) {
            return;
        }

        const token = localStorage.getItem("jwtToken");

        // Disable button during deletion
        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

        // Call API to delete project
        fetch(`/projects/delete/${projectId}`, {
            method: "DELETE",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            }
        })
        .then(response => {
            if (response.status === 401) {
                alert("Session expired. Please log in again.");
                localStorage.removeItem('jwtToken');
                window.location.href = "/login";
                return;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.success) {
                // Delete was successful
                // Remove project from allProjects array
                const projectIndex = allProjects.findIndex(p => p.projectIdentifier === projectId);
                if (projectIndex !== -1) {
                    allProjects.splice(projectIndex, 1);
                }

                // Refresh the table with current filter
                const activeFilterBtn = document.querySelector('.filter-btn.active');
                const currentFilter = activeFilterBtn ? activeFilterBtn.getAttribute('data-status') : 'ALL';

                if (currentFilter === 'ALL') {
                    populateTable(allProjects);
                } else {
                    const filteredProjects = allProjects.filter(project => project.status === currentFilter);
                    populateTable(filteredProjects);
                }

            } else if (data && data.error) {
                alert("Failed to delete project: " + data.error);
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-trash"></i>';
            } else {
                alert("Failed to delete project.");
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-trash"></i>';
            }
        })
        .catch(error => {
            console.error("Error deleting project:", error);
            alert("Error deleting project. Please try again.");
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-trash"></i>';
        });
    }

    // Handle View Vendors button
    if (event.target.closest('.view-vendors')) {
        const token = localStorage.getItem("jwtToken");
        // Read from the button itself: on mobile, DataTables Responsive moves the
        // button into a child row, so row.children[0] is not the project id cell.
        const projectId = event.target.closest('.view-vendors').getAttribute('data-project-id');

        // Fetch vendor list for the project
        fetch('/projects/vendor-list', {
            method: "POST",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                projectId: projectId
            })
        })
        .then(response => {
            if (response.status === 401) {
                alert("Session expired. Please log in again.");
                localStorage.removeItem('jwtToken');
                window.location.href = "/login";
                return;
            }
            return response.json();
        })
        .then(data => {
            if (!data) {
                console.error("No data received from vendor-list API");
                return;
            }

            const vendorList = document.getElementById('vendorList');
            vendorList.innerHTML = ""; // Clear previous content

            // Check if data is an array and has items
            if (Array.isArray(data) && data.length > 0) {
                // Display vendors with their links
                data.forEach(vendorLink => {
                    const li = document.createElement('li');
                    li.className = 'mb-2';

                    // Create vendor name and link display
                    li.innerHTML = `
                        <strong>${vendorLink.vendorName}:</strong><br>
                        <a href="${vendorLink.link}" target="_blank" class="text-primary">
                            ${vendorLink.link}
                        </a>
                    `;

                    vendorList.appendChild(li);
                });
            } else {
                // No vendors found for this project
                const li = document.createElement('li');
                li.textContent = 'No vendors assigned to this project.';
                li.className = 'text-muted';
                vendorList.appendChild(li);
            }

            // Show the modal using Bootstrap's API
            const vendorsModal = bootstrap.Modal.getOrCreateInstance(document.getElementById('vendorsModal'));
            vendorsModal.show();
        })
        .catch(error => {
            console.error('Error fetching vendor list:', error);
            alert('Error loading vendor list. Please try again.');
        });
    }
});

function initializeDataTable() {
    var allText = window.allText || 'All';
    dataTable = $("#example").DataTable({
        responsive: true,
        lengthMenu: [
            [10, 20, 50, 100, -1],
            [10, 20, 50, 100, allText]
        ],
        pageLength: 100,
        dom: 'lfrtip',
        language: {
            search: "Search Table:",
            lengthMenu: "Show _MENU_ entries",
        },
        order: [] // No initial sorting - preserve API order (newest projects first by createdAt DESC)
    });
}