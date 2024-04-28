
# Antibloater

Antibloater is an Android application designed to streamline the process of removing bloatware apps from your device. It establishes a local ADB connection to execute commands selected by users for removing unwanted pre-installed apps.

## Features

- **User-Friendly Interface**: Antibloater provides a simple and intuitive interface for users to select and remove bloatware apps hassle-free.
- **Local ADB Connection**: The app leverages a local ADB connection to execute removal commands directly on the device.
- **Customizable**: Users can choose which apps they want to remove, giving them full control over the debloating process.

## Dependencies

Antibloater relies on the following dependencies:

- androidx.core:core-ktx:1.8.0
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.11.0
- androidx.constraintlayout:constraintlayout:2.1.4
- junit:junit:4.13.2 (for testing)
- androidx.test.ext:junit:1.1.5 (for instrumented unit tests)
- androidx.test.espresso:espresso-core:3.5.1 (for UI testing)
- androidx.preference:preference-ktx:1.2.0
- androidx.fragment:fragment-ktx:1.6.0
- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1
- androidx.lifecycle:lifecycle-livedata-ktx:2.6.1
- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
- com.google.android.material:material:1.9.0

## ADB Class

The ADB class used in this project is ported from the open-source project [LADB](https://github.com/tytydraco/LADB).

## Getting Started

To use Antibloater, follow these steps:

1. Clone the repository to your local machine.
2. Open the project in Android Studio.
3. Build and run the project on your Android device.

## Contributing

Contributions are welcome! If you'd like to contribute to Antibloater, please fork the repository and submit a pull request with your changes.

## License

This project is licensed under the [MIT License](LICENSE).

---

Feel free to customize this template according to your app's specific features and requirements. Make sure to include any additional information or instructions that would be helpful for users and contributors.
