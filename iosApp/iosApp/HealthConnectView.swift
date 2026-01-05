//
//  HealthConnectView.swift
//  iosApp
//
//  Created by Daniel Ramzani on 23/06/2024.
//  Copyright © 2024 orgName. All rights reserved.
//

import SwiftUI

struct HealthConnectView: View {
    @State private var steps: String = "0"
    @State private var mins: String = "0"
    @State private var distance: String = "0"
    @State private var sleepDuration: String = "00:00"
    @State private var isAuthorized: Bool = false

    private let healthKitManager = HealthKitManager()

    var body: some View {
        VStack {
            if isAuthorized {
                VStack {
                    DataItemView(label: "Steps", value: $steps, duration: "Today")
                    DataItemView(label: "Active minutes", value: $mins, duration: "Today")
                    DataItemView(label: "Distance", value: $distance, duration: "Today")
                    DataItemView(label: "Sleep", value: $sleepDuration, duration: "Last session")
                }
                .onAppear {
                    healthKitManager.checkAuthorization { success in
                        isAuthorized = success
                        if success {
                            // Fetch the actual data from HealthKit
                            healthKitManager.getSteps { stepsData, error  in
                                if error == nil {
                                    steps = stepsData
                                }
                            }
                            healthKitManager.getActiveMinutes { activeMinutesData, error  in
                                if error == nil {
                                    mins = activeMinutesData
                                }
                            }
                            healthKitManager.getDistance { distanceData, error in
                                if error == nil {
                                    distance = distanceData
                                }
                            }
                            healthKitManager.getSleepDuration { sleepDurationData, error in
                                if error == nil {
                                    sleepDuration = sleepDurationData
                                }
                            }
                        }
                    }
                }
            } else {
                // Ask for permissions or handle the unauthorized state
                Text("Unauthorized")
            }
        }
    }
}

struct DataItemView: View {
    let label: String
    @Binding var value: String
    let duration: String

    var body: some View {
        VStack {
            Text(label)
                .font(.title2)
            Divider()
            Text(duration)
                .font(.caption)
            Text(value)
                .font(.title)
        }
        .padding()
        .background(Color.gray.opacity(0.2))
        .cornerRadius(10)
    }
}
