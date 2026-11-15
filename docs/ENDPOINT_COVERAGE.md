> Names in this inventory are sanitized BookingCore equivalents of supplied reference names; they are requirements labels, not supported HTTP aliases.

# Legacy endpoint inventory and feature coverage

All 273 supplied unique paths are preserved below. Only endpoint names were used; registration code, namespaces, callbacks and comments were excluded.

These are feature references, **not registered legacy aliases**. Consumers use the canonical `/api/v1` API documented in OpenAPI. The original payloads and full business rules have not been provided.

| Status | Meaning | Paths |
| --- | --- | --- |
| AVAILABLE | A corresponding canonical core operation exists; legacy request/response or full behavioural parity is not established. | 16 |
| PARTIAL | Related operations exist, but part of the named workflow is missing. | 120 |
| PLANNED | No corresponding feature is implemented yet. | 137 |

Machine-readable inventory: [endpoint-inventory.json](endpoint-inventory.json). The counts refer to supplied paths, not the number of implemented API routes.

| Legacy path | Domain | Status | Canonical interfaces |
| --- | --- | --- | --- |
| `/validateactivereceipt` | Subscriptions and app-store billing | PLANNED |  |
| `/ll_subscription_purchased` | Subscriptions and app-store billing | PLANNED |  |
| `/statusiosubscription` | Subscriptions and app-store billing | PLANNED |  |
| `/handleAppleNotification` | Subscriptions and app-store billing | PLANNED |  |
| `/fetchLatestVersion` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/bookingcore_login` | Authentication | AVAILABLE | `POST /api/v1/auth/token`; `POST /api/v1/auth/login`; `POST /api/v1/auth/logout` |
| `/bookingcore_createaccount` | Account registration | PARTIAL | `POST /api/v1/auth/workspaces`; `POST /api/v1/auth/register`; `POST /api/v1/invitations`; `POST /api/v1/auth/invitations/accept` |
| `/bookingcore_createaccount_parent` | Account registration | PARTIAL | `POST /api/v1/auth/workspaces`; `POST /api/v1/auth/register`; `POST /api/v1/invitations`; `POST /api/v1/auth/invitations/accept` |
| `/forgotpassword` | Password recovery | PLANNED |  |
| `/resetpassword` | Password recovery | PLANNED |  |
| `/getinvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/declineinvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/deleteinvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/getpendinginvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/sendinvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/approveinvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/newcoachinvitation` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/newcoachinvite` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/updatexpirydate` | Invitations and relationships | PARTIAL | `GET /api/v1/invitations` |
| `/getstudentlist` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/getcoachlist` | Provider catalogue | AVAILABLE | `GET /api/v1/providers` |
| `/getstudentcoaches` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/getstudentcoachesdata` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/getmanagedstudentcoachesdata` | Guardians and managed students | PLANNED |  |
| `/getnewstudentcoachesdata` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/getuserdetails` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/loginuserdetails` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/sendotp` | Phone verification | PLANNED |  |
| `/checkphonexist` | Phone verification | PLANNED |  |
| `/checkphonexist2` | Phone verification | PLANNED |  |
| `/getnotsignedscreens` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/settingsapi` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/getsettingsapi` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/getscheduledlessons` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/getscheduledlessons_managed` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/studentlessonshistory` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/studentslessonshistory` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/studentslessonshistorypaging` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/coachavailability` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/instructordashboardata` | Reporting and dashboards | PARTIAL | `GET /api/v1/booking-events` |
| `/coachscheduledlessons` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/change_password_account` | Identity and profiles | AVAILABLE | `POST /api/v1/me/password` |
| `/add_custom_field` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/add_new_location` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/edit_location` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/delete_location` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/my_availability` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/schedule_availability` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/student_myavailability` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/student_availability` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/student_schedule_Availability` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/student_availability2` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/closelessons` | Attendance and retention | PLANNED |  |
| `/getnotcloselessoncounts` | Attendance and retention | PLANNED |  |
| `/closedlessonshistory` | Attendance and retention | PLANNED |  |
| `/closedlessonshistorypaging` | Attendance and retention | PLANNED |  |
| `/markedlesson` | Attendance and retention | PLANNED |  |
| `/markedalllesson` | Attendance and retention | PLANNED |  |
| `/create_schedule_api` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/create_coach_schedule` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/edit_coachschedule` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/add_coach_lesson_product` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/edit_coach_lesson_product` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/get_all_lessons` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/savecoachnotes` | Coaching notes | PLANNED |  |
| `/savestudentnotes` | Coaching notes | PLANNED |  |
| `/coach_notes_to_linkreq` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/delete_relationship` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/update_category_name` | Categories, tags and custom fields | PLANNED |  |
| `/delete_coachschedule` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/delete_coachschedule_block` | Provider blockouts | AVAILABLE | `POST /api/v1/providers/{id}/blockouts`; `GET /api/v1/providers/{id}/blockouts`; `DELETE /api/v1/providers/{id}/blockouts/{block}` |
| `/delete_category` | Categories, tags and custom fields | PLANNED |  |
| `/deletecontact` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/relinkedcoach` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/asklinklinkcoach` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/unlinkcoach` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/insert_new_category` | Categories, tags and custom fields | PLANNED |  |
| `/set_default_category` | Categories, tags and custom fields | PLANNED |  |
| `/coach_blockedschedules` | Provider blockouts | AVAILABLE | `POST /api/v1/providers/{id}/blockouts`; `GET /api/v1/providers/{id}/blockouts`; `DELETE /api/v1/providers/{id}/blockouts/{block}` |
| `/create_coach_scheduleblock` | Provider blockouts | AVAILABLE | `POST /api/v1/providers/{id}/blockouts`; `GET /api/v1/providers/{id}/blockouts`; `DELETE /api/v1/providers/{id}/blockouts/{block}` |
| `/edit_coachscheduleblock` | Provider blockouts | AVAILABLE | `POST /api/v1/providers/{id}/blockouts`; `GET /api/v1/providers/{id}/blockouts`; `DELETE /api/v1/providers/{id}/blockouts/{block}` |
| `/delete_coach_lesson_product` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/getcoachdetailsbyid` | Provider catalogue | AVAILABLE | `GET /api/v1/providers` |
| `/cancelappointment` | Booking lifecycle | AVAILABLE | `POST /api/v1/bookings/{id}/cancel` |
| `/cancelappointment2` | Booking lifecycle | AVAILABLE | `POST /api/v1/bookings/{id}/cancel` |
| `/reschedule_appointment` | Booking lifecycle | AVAILABLE | `POST /api/v1/bookings/{id}/reschedule` |
| `/delete_appointments_by_author` | Attendance and retention | PLANNED |  |
| `/update_all_lessons_interval_time` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/automarkedlesson` | Attendance and retention | PLANNED |  |
| `/savemulticartmodalsettings` | Cart and checkout | PARTIAL | `POST /api/v1/bookings/batch`; `POST /api/v1/sessions/{id}/enrolments` |
| `/getStudentsofInstructor` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/sendmultilesson_booking_notification` | Notifications | PARTIAL | `GET /api/v1/notifications`; `POST /api/v1/notifications/{id}/read` |
| `/addsemilessonstocart` | Cart and checkout | PARTIAL | `POST /api/v1/bookings/batch`; `POST /api/v1/sessions/{id}/enrolments` |
| `/deletesemilessonstocart` | Cart and checkout | PARTIAL | `POST /api/v1/bookings/batch`; `POST /api/v1/sessions/{id}/enrolments` |
| `/payment` | Payments, orders and payouts | PLANNED |  |
| `/getcoachwalletamount` | Wallets and credits | PLANNED |  |
| `/updatecoachwalletamount` | Wallets and credits | PLANNED |  |
| `/getCoachWalletTransactions` | Wallets and credits | PLANNED |  |
| `/getCoachWalletTransactionspaging` | Wallets and credits | PLANNED |  |
| `/getLocationCredits` | Wallets and credits | PLANNED |  |
| `/payment-sheet` | Payments, orders and payouts | PLANNED |  |
| `/pay-from-saved-card` | Payments, orders and payouts | PLANNED |  |
| `/retrive-stripe-customer-by-userid` | Payments, orders and payouts | PLANNED |  |
| `/update-default-stripe-card` | Payments, orders and payouts | PLANNED |  |
| `/delete-stripe-card` | Payments, orders and payouts | PLANNED |  |
| `/edit-stripe-card` | Payments, orders and payouts | PLANNED |  |
| `/create_stripe_card` | Payments, orders and payouts | PLANNED |  |
| `/resendinvoice` | Payments, orders and payouts | PLANNED |  |
| `/insert_new_subcategory` | Categories, tags and custom fields | PLANNED |  |
| `/update_customer_cart_itemdata` | Cart and checkout | PARTIAL | `POST /api/v1/bookings/batch`; `POST /api/v1/sessions/{id}/enrolments` |
| `/update_customer_cart_itemdata_notes` | Cart and checkout | PARTIAL | `POST /api/v1/bookings/batch`; `POST /api/v1/sessions/{id}/enrolments` |
| `/createstripeconnectedaccountforcoach` | Payments, orders and payouts | PLANNED |  |
| `/retrievestripeconnectedaccountforcoach` | Payments, orders and payouts | PLANNED |  |
| `/loginforstripeconnectedaccountforcoach` | Payments, orders and payouts | PLANNED |  |
| `/getcoachstripefeesettings` | Business fees and payment policies | PLANNED |  |
| `/get_onboarding_process_checks` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/update_coach_lesson_settings` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/profile_image` | Files, waivers and signatures | PLANNED |  |
| `/get_all_managedstudent` | Guardians and managed students | PLANNED |  |
| `/checkonlyphonexist` | Phone verification | PLANNED |  |
| `/create_managed_student` | Guardians and managed students | PLANNED |  |
| `/create_adultmanaged_student` | Guardians and managed students | PLANNED |  |
| `/resend_mstinfo` | Guardians and managed students | PLANNED |  |
| `/update_mstdetails` | Guardians and managed students | PLANNED |  |
| `/removeManagedStudentLogin` | Guardians and managed students | PLANNED |  |
| `/sendnotification_instructorbooking` | Notifications | PARTIAL | `GET /api/v1/notifications`; `POST /api/v1/notifications/{id}/read` |
| `/book_lesson_all_coach_data` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/updateExpoPushTokens` | Notifications | PARTIAL | `GET /api/v1/notifications`; `POST /api/v1/notifications/{id}/read` |
| `/deleteManagedstudent` | Guardians and managed students | PLANNED |  |
| `/accountdeletionsubmit` | Account and membership lifecycle | PLANNED |  |
| `/remove_profile_image` | Files, waivers and signatures | PLANNED |  |
| `/coachscheduledlessons_repeated` | Recurring schedules | PARTIAL | `POST /api/v1/session-series` |
| `/create_coach_schedule_repeated` | Recurring schedules | PARTIAL | `POST /api/v1/session-series` |
| `/edit_coach_schedule_repeated` | Recurring schedules | PARTIAL | `POST /api/v1/session-series` |
| `/GetAppointmentDatesForCoach_1151` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/get_coachavailability_portal` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/get_coach_lessons_portal` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/sessions` |
| `/get_appointment_contact_portal` | Contacts and relationships | PLANNED |  |
| `/get_coach_time_slot_by_date_1151` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/get_coach_time_slot_by_date_1151_test` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/book_lesson_all_coach_data_1151` | Booking discovery and lifecycle | PARTIAL | `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}`; `POST /api/v1/bookings`; `POST /api/v1/bookings/batch` |
| `/coach_blockedschedules_repeated` | Recurring schedules | PARTIAL | `POST /api/v1/session-series` |
| `/create_coach_scheduleblock_repeated` | Recurring schedules | PARTIAL | `POST /api/v1/session-series` |
| `/edit_coachscheduleblock_repeated` | Recurring schedules | PARTIAL | `POST /api/v1/session-series` |
| `/getcoachesdata` | Provider catalogue | AVAILABLE | `GET /api/v1/providers` |
| `/connectlinkwithins` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/dashboard_loginforportal` | Authentication | AVAILABLE | `POST /api/v1/auth/token`; `POST /api/v1/auth/login`; `POST /api/v1/auth/logout` |
| `/dashboard_logoutforportal` | Authentication | AVAILABLE | `POST /api/v1/auth/token`; `POST /api/v1/auth/login`; `POST /api/v1/auth/logout` |
| `/dashboard_data/(?P<user_id>\d+)/(?P<locationid>\d+)` | Reporting and dashboards | PARTIAL | `GET /api/v1/booking-events` |
| `/dashboard_data_opt/(?P<user_id>\d+)/(?P<locationid>\d+)` | Reporting and dashboards | PARTIAL | `GET /api/v1/booking-events` |
| `/get_reports/(?P<user_id>\d+)/(?P<locationid>\d+)/(?P<startdate>\d+)/(?P<enddate>\d+)` | Reporting and dashboards | PARTIAL | `GET /api/v1/booking-events` |
| `/get_reports2/(?P<user_id>\d+)/(?P<locationid>\d+)/(?P<startdate>\d+)/(?P<enddate>\d+)` | Reporting and dashboards | PARTIAL | `GET /api/v1/booking-events` |
| `/get_link_requests/(?P<userid>\d+)/(?P<locationid>\d+)` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/getCoachesByLocation/(?P<userid>\d+)/(?P<locationid>\d+)/(?P<showallloc>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/getCoachesByLocation/(?P<userid>\d+)/(?P<locationid>\d+)/(?P<showallloc>true&#124;false)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/get_appointment_dates_for_coach_for_dashboard/(?P<userid>\d+)/(?P<locationid>\d+)` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/create_coach_invitation_from_dashboard` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/signup_dashboard` | Account registration | PARTIAL | `POST /api/v1/auth/workspaces`; `POST /api/v1/auth/register`; `POST /api/v1/invitations`; `POST /api/v1/auth/invitations/accept` |
| `/save_settings_company_info/(?P<userid>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/save_settings_location_info/(?P<userid>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/update_settings_location_info/(?P<userid>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/update_settings_location_info2/(?P<userid>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/update_location_time/(?P<userid>\d+)/(?P<locationid>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/delete_settings_location_info/(?P<userid>\d+)/(?P<locationid>\d+)` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/retrieveStripeConnectedAccountForlocation/(?P<Accid>\d+)` | Payments, orders and payouts | PLANNED |  |
| `/loginForStripeConnectedAccountForlocation/(?P<Accid>\d+)` | Payments, orders and payouts | PLANNED |  |
| `/matchLocation` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/getportallocationlist` | Locations and rooms | PARTIAL | `POST /api/v1/locations`; `GET /api/v1/locations`; `PUT /api/v1/locations/{id}`; `POST /api/v1/locations/{id}/rooms` |
| `/approveinvitationbygym/(?P<invite_id>\d+)/(?P<authorid>\d+)` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/retriveconnectbalance` | Payments, orders and payouts | PLANNED |  |
| `/changepaymentscheduleofcoachonstripe` | Payments, orders and payouts | PLANNED |  |
| `/updateStripePayoutScheduleforlocation` | Payments, orders and payouts | PLANNED |  |
| `/request_password_reset_forportal` | Password recovery | PLANNED |  |
| `/validate_password_reset_forportal` | Password recovery | PLANNED |  |
| `/reset_password_forportal` | Password recovery | PLANNED |  |
| `/forgot_password_forportal` | Password recovery | PLANNED |  |
| `/instant_payout` | Payments, orders and payouts | PLANNED |  |
| `/getUsersByLocation` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/updateGlobalLocationFeeSettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateGlobalCommissionFeeSettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateGlobalCashPaySettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateGlobalHybridCashPaySettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateGlobalComplimentaryCashPaySettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateindividualLocationFeeSettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateindividualCommissionFeeSettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateindividualCashPaySettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateindividualHybridCashPaySettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateindividualblockInstructor4Location/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/updateindividualComplimentaryCashPaySettings/(?P<userid>\d+)` | Business fees and payment policies | PLANNED |  |
| `/savenewuserfromlocationdashboard/(?P<userid>\d+)` | Account registration | PARTIAL | `POST /api/v1/auth/workspaces`; `POST /api/v1/auth/register`; `POST /api/v1/invitations`; `POST /api/v1/auth/invitations/accept` |
| `/updateordeleteuserfromlocationdashboard/(?P<userid>\d+)` | Account and membership lifecycle | PLANNED |  |
| `/getnewuserforlocationdashboard/(?P<business_id>\d+)` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/getinvitation2` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/approveinvitation2` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/addlessonplan` | Lesson plans and progress | PLANNED |  |
| `/getlessonplans` | Lesson plans and progress | PLANNED |  |
| `/getlessonplandetail` | Lesson plans and progress | PLANNED |  |
| `/editlessonplan` | Lesson plans and progress | PLANNED |  |
| `/updatestatuslessonplan` | Lesson plans and progress | PLANNED |  |
| `/getskillsofcoachstudent` | Lesson plans and progress | PLANNED |  |
| `/deletelessonplan` | Lesson plans and progress | PLANNED |  |
| `/getlessonplans_bycoach` | Lesson plans and progress | PLANNED |  |
| `/getlessonplans_forcoach` | Lesson plans and progress | PLANNED |  |
| `/add_lesson_category` | Lesson plans and progress | PLANNED |  |
| `/edit_lessonplan_category` | Lesson plans and progress | PLANNED |  |
| `/delete_lessonplan_category` | Lesson plans and progress | PLANNED |  |
| `/getlessoncategorylist` | Lesson plans and progress | PLANNED |  |
| `/assignlessontocoach` | Lesson plans and progress | PLANNED |  |
| `/assignlessontostudent` | Lesson plans and progress | PLANNED |  |
| `/getassignedlessonsofstudent` | Lesson plans and progress | PLANNED |  |
| `/addskillprogressforstudent` | Lesson plans and progress | PLANNED |  |
| `/completelesson` | Lesson plans and progress | PLANNED |  |
| `/getskillprofilestudent` | Lesson plans and progress | PLANNED |  |
| `/addprogressreportforskill` | Lesson plans and progress | PLANNED |  |
| `/editprogressreportforskill` | Lesson plans and progress | PLANNED |  |
| `/getprogressreportforskill` | Lesson plans and progress | PLANNED |  |
| `/addprogressreportgeneral` | Lesson plans and progress | PLANNED |  |
| `/editprogressreportgeneral` | Lesson plans and progress | PLANNED |  |
| `/updateprogressreportreadstatusgeneral` | Lesson plans and progress | PLANNED |  |
| `/getprogressreportgeneral` | Lesson plans and progress | PLANNED |  |
| `/getprogressreportforall` | Lesson plans and progress | PLANNED |  |
| `/getgeneralprreadstatuscount` | Lesson plans and progress | PLANNED |  |
| `/deleteprogressreportgeneral` | Lesson plans and progress | PLANNED |  |
| `/getassignedlessonsofstudentloggedin` | Lesson plans and progress | PLANNED |  |
| `/deleteassignedlessonsofstudent` | Lesson plans and progress | PLANNED |  |
| `/tipproduct` | Gratuities | PLANNED |  |
| `/tippricetocart` | Gratuities | PLANNED |  |
| `/getcoachtipssettings` | Gratuities | PLANNED |  |
| `/getnotificationarea` | Notifications | PARTIAL | `GET /api/v1/notifications`; `POST /api/v1/notifications/{id}/read` |
| `/updatenotificationarea` | Notifications | PARTIAL | `GET /api/v1/notifications`; `POST /api/v1/notifications/{id}/read` |
| `/stripe-pay-handler` | Payments, orders and payouts | PLANNED |  |
| `/stripe-account-updated` | Payments, orders and payouts | PLANNED |  |
| `/account_session` | Payments, orders and payouts | PLANNED |  |
| `/createstripeconnectedaccountforcoachportal` | Payments, orders and payouts | PLANNED |  |
| `/get_onboarding_process_checks_portal` | Application configuration | PARTIAL | `GET /api/v1/business/settings`; `PUT /api/v1/business/settings`; `GET /api/v1/capabilities` |
| `/saveprofileandsecurityinfo` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/saveprofileandsecurityinfopw` | Identity and profiles | AVAILABLE | `POST /api/v1/me/password` |
| `/updatepaymenttransferforsheet` | Payments, orders and payouts | PLANNED |  |
| `/upload_docs` | Files, waivers and signatures | PLANNED |  |
| `/get_files_by_userid` | Files, waivers and signatures | PLANNED |  |
| `/delete_files_by_userid` | Files, waivers and signatures | PLANNED |  |
| `/servepdf` | Files, waivers and signatures | PLANNED |  |
| `/getincartappointments` | Cart and checkout | PARTIAL | `POST /api/v1/bookings/batch`; `POST /api/v1/sessions/{id}/enrolments` |
| `/upload_signature_to_server` | Files, waivers and signatures | PLANNED |  |
| `/delete_instructor_from_portal/(?P<locationid>\d+)/(?P<userid>\d+)` | Account and membership lifecycle | PLANNED |  |
| `/delete_linkrequest_from_portal/(?P<locationid>\d+)/(?P<userid>\d+)` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/iapsubscriptionpurchased` | Subscriptions and app-store billing | PLANNED |  |
| `/markcashorderaspaid` | Payments, orders and payouts | PLANNED |  |
| `/handleGooglePlayNotification` | Subscriptions and app-store billing | PLANNED |  |
| `/checkandroidsubscriptionstatus` | Subscriptions and app-store billing | PLANNED |  |
| `/checkIosSubscriptionStatus` | Subscriptions and app-store billing | PLANNED |  |
| `/createPaymentIntent` | Payments, orders and payouts | PLANNED |  |
| `/checkstripesubscriptionstatus` | Subscriptions and app-store billing | PLANNED |  |
| `/BookingCore-login` | Authentication | AVAILABLE | `POST /api/v1/auth/token`; `POST /api/v1/auth/login`; `POST /api/v1/auth/logout` |
| `/getlocationscreditbyuserid` | Wallets and credits | PLANNED |  |
| `/getlocationscreditbylocid` | Wallets and credits | PLANNED |  |
| `/getallfiltercategory` | Categories, tags and custom fields | PLANNED |  |
| `/getcoachfiltertaglist` | Categories, tags and custom fields | PLANNED |  |
| `/updatecoachfiltertag` | Categories, tags and custom fields | PLANNED |  |
| `/getpredefinedlessons` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/coach_availability_for_predefinedlesson` | Availability and sessions | PARTIAL | `GET /api/v1/providers/{id}/slots`; `GET /api/v1/providers/{id}/availability`; `POST /api/v1/providers/{id}/availability`; `DELETE /api/v1/providers/{id}/availability/{window}`; `POST /api/v1/sessions`; `POST /api/v1/session-series` |
| `/get_all_lessonsandrates` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/add_coach_definedlesson_product` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/edit_coach_definedlesson_product` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/get_coach_definedlesson_availabilities_1151` | Service catalogue | PARTIAL | `GET /api/v1/services`; `POST /api/v1/services`; `PUT /api/v1/providers/{id}/services`; `GET /api/v1/sessions` |
| `/add_lesson_room` | Locations and rooms | PARTIAL | `POST /api/v1/locations/{id}/rooms`; `GET /api/v1/locations/{id}/rooms` |
| `/edit_lesson_room` | Locations and rooms | PARTIAL | `POST /api/v1/locations/{id}/rooms`; `GET /api/v1/locations/{id}/rooms` |
| `/delete_lesson_room` | Locations and rooms | PARTIAL | `POST /api/v1/locations/{id}/rooms`; `GET /api/v1/locations/{id}/rooms` |
| `/get-ordersdetails` | Payments, orders and payouts | PLANNED |  |
| `/get-ordersdetails_coach` | Payments, orders and payouts | PLANNED |  |
| `/get_associateduserinfo` | Identity and profiles | PARTIAL | `GET /api/v1/me/profile`; `PUT /api/v1/me/profile`; `GET /api/v1/users`; `GET /api/v1/users/{id}` |
| `/connection_respond` | Invitations and relationships | PARTIAL | `POST /api/v1/invitations`; `GET /api/v1/invitations`; `POST /api/v1/invitations/{id}/revoke`; `POST /api/v1/auth/invitations/accept` |
| `/getgymfeesettings/(?P<business_id>\d+)` | Business fees and payment policies | PLANNED |  |
