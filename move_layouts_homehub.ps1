# Move HomeHub layouts to domain subdirectories
$root = "app/src/main/res/layout"
$base = "app/src/main/res/layouts"

function Move-Layout($file, $domain) {
    $src = "$root/$file"
    $dest = "$base/$domain/layout/$file"
    if (Test-Path $src) {
        Write-Host "Moving $file to $domain"
        Move-Item -Path $src -Destination $dest -Force
    }
}

# Domains
$auth = @("activity_login.xml", "activity_phone_auth.xml", "activity_private_access.xml", "sign_up.xml")
$student = @("activity_student_dashboard.xml", "activity_student_edit_profile.xml", "activity_student_profile.xml", "activity_favorites.xml", "item_student.xml", "item_cart.xml", "activity_edit_profile_v8.xml")
$supplier = @("activity_add_water_service.xml", "activity_water_supplier_dashboard.xml", "activity_water_supplier_notifications.xml", "activity_water_supplier_profile.xml", "activity_water_suppliers.xml", "activity_supplier_orders.xml", "item_water_supplier.xml", "item_supplier_order.xml")
$chat = @("activity_chat.xml", "activity_chat_list.xml", "item_chat_room.xml", "item_message.xml", "item_message_thread.xml", "item_ms_received.xml", "item_ms_received_reply.xml", "item_ms_sent.xml", "item_ms_sent_reply.xml", "item_ms_system.xml")
$billing = @("activity_booking_details.xml", "activity_my_bookings.xml", "activity_payment_details.xml", "activity_rent_tracking.xml", "item_booking.xml", "layout_mpesa_payment.xml")
$admin = @("activity_admin_dashboard.xml", "activity_admin_notifications.xml", "activity_admin_profile.xml", "activity_analytics.xml", "activity_application_details_v8.xml", "activity_caretaker_details.xml", "activity_manage_applications.xml", "activity_manage_caretakers.xml", "activity_manage_properties.xml", "activity_manage_students.xml", "activity_suspended_users.xml", "activity_verified_users.xml", "activity_performance_v8.xml", "activity_manage_users.xml", "activity_id_verification.xml", "activity_reports.xml", "fragment_admin_profile.xml", "item_admin.xml", "item_admin_property.xml", "item_recent_activity.xml", "item_application.xml", "item_caretaker.xml", "item_tenant_risk.xml", "item_analytics_booking.xml", "item_analytics_booking_modern.xml", "item_analytics_insight.xml", "item_analytics_insight_modern.xml", "dialog_add_caretaker.xml", "dialog_add_student_admin.xml", "dialog_caretaker_verification.xml", "dialog_add_note.xml", "view_analytics_bar.xml", "view_analytics_bar_modern.xml")
$caretaker = @("activity_caretaker_application.xml", "activity_caretaker_dashboard.xml", "activity_caretaker_messages.xml", "activity_caretaker_notifications.xml", "activity_caretaker_profile.xml", "activity_my_properties.xml", "fragment_caretaker_requests.xml", "fragment_host_requests.xml", "item_caretaker_properties.xml", "item_caretaker_request.xml", "item_my_property.xml", "item_host_properties.xml", "item_host_property.xml")
$property = @("activity_add_property.xml", "activity_all_properties.xml", "activity_all_property.xml", "activity_filter_v8.xml", "activity_property_details.xml", "activity_reviews.xml", "activity_room_manager.xml", "fragment_properties.xml", "item_property.xml", "item_property1.xml", "item_property_image.xml", "item_property_image_detail.xml", "item_property_image_preview.xml", "item_property_modern.xml", "item_property_performance.xml", "item_property_vertical.xml", "item_house_grid.xml", "item_house_horizontal.xml", "item_house_vertical.xml", "item_room_management.xml", "item_room_selection.xml", "item_room_type_edit.xml", "item_amenity.xml", "item_amenity_display.xml", "item_amenity_simple.xml", "item_feature.xml", "item_category.xml", "item_image_slider.xml", "item_room_image.xml", "item_room_images.xml", "item_review.xml", "dialog_add_room.xml", "dialog_add_room_type.xml", "dialog_reviews.xml", "reviews_dialog.xml", "view_rating_bar_row.xml", "view_review_info_row_v8.xml")
$other = @("activity_main.xml", "activity_splash.xml", "activity_settings.xml", "activity_notifications.xml", "activity_maintenance.xml", "activity_image_viewer.xml", "activity_location_details.xml", "activity_map_location_picker.xml", "activity_role_selection.xml", "activity_user_edit_profile.xml", "item_notifications.xml", "layout_document_verification_bottom_sheet.xml", "layout_name_confirmation.xml", "layout_profile_item.xml", "layout_profile_item_modern.xml", "layout_section_header.xml", "bg_header_rounded_bottom.xml", "dialog_fullscreen_image.xml", "dialog_location_summary.xml", "dialog_phone_numbers.xml", "dialog_qr_code.xml", "dialog_set_password.xml", "spinner_dropdown_item.xml", "spinner_item.xml", "test_notification_button.xml")

$auth | ForEach-Object { Move-Layout $_ "auth" }
$student | ForEach-Object { Move-Layout $_ "student" }
$supplier | ForEach-Object { Move-Layout $_ "supplier" }
$property | ForEach-Object { Move-Layout $_ "property" }
$chat | ForEach-Object { Move-Layout $_ "chat" }
$billing | ForEach-Object { Move-Layout $_ "billing" }
$admin | ForEach-Object { Move-Layout $_ "admin" }
$caretaker | ForEach-Object { Move-Layout $_ "caretaker" }
$other | ForEach-Object { Move-Layout $_ "other" }

Write-Host "Reorganization complete!"
