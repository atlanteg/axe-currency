import SwiftUI

struct ContentView: View {
    @StateObject private var vm = ConverterViewModel()
    @FocusState private var focusedCode: String?

    @State private var showAdd = false
    @State private var showSettings = false
    @State private var showInfo = false
    @State private var deleteCandidate: String?
    @State private var dragged: String?

    var body: some View {
        VStack(spacing: 0) {
            header
            brandRow
            currencyList
            footer
        }
        .background(Color.bgGray)
        .environment(\.layoutDirection, L10n.isRTL ? .rightToLeft : .leftToRight)
        .id(vm.langVersion)   // смена языка → полная перерисовка
        .task {
            await vm.refresh()
            await vm.loadSourceCodes()
            if ScreenshotArgs.openAdd { showAdd = true }
            if ScreenshotArgs.openSettings { showSettings = true }
            if ScreenshotArgs.openInfo { showInfo = true }
        }
        .sheet(isPresented: $showAdd) { AddCurrencyView(vm: vm) }
        .sheet(isPresented: $showSettings) { SettingsView(vm: vm) }
        .sheet(isPresented: $showInfo) { SourceInfoView() }
        .alert(L10n.t("delete_currency_title"), isPresented: .init(
            get: { deleteCandidate != nil }, set: { if !$0 { deleteCandidate = nil } })) {
            Button(L10n.t("delete"), role: .destructive) {
                if let c = deleteCandidate { vm.remove(c) }
                deleteCandidate = nil
            }
            Button(L10n.t("cancel"), role: .cancel) { deleteCandidate = nil }
        } message: {
            if let c = deleteCandidate {
                Text(L10n.t("delete_currency_msg", c, CurrencyData.name(c)))
            }
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text(L10n.t("selected", vm.currencies.count))
                .font(.caption.bold())
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(Color(hex: "E8EEF9"))
                .foregroundColor(.brandBlue)
                .cornerRadius(6)
            Group {
                if let e = vm.errorText { Text("⚠ \(e)") }
                else if vm.lastUpdated.isEmpty { Text(L10n.t("loading")) }
                else { Text(L10n.t("updated", vm.lastUpdated, vm.currentSource)) }
            }
            .font(.caption).foregroundColor(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            if vm.isLoading { ProgressView().scaleEffect(0.8) }
            Button { Task { await vm.refresh() } } label: {
                Image(systemName: "arrow.clockwise").foregroundColor(.brandBlue)
            }
            Button { showSettings = true } label: {
                Image(systemName: "gearshape").foregroundColor(.gray)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .readableWidth()
        .background(Color.white)
    }

    private var brandRow: some View {
        ZStack {
            Image("LogoMini").resizable().scaledToFit().frame(height: 34)
            HStack {
                Text("FIXXE")
                    .font(.subheadline.bold()).kerning(1.5)
                    .foregroundColor(.brandGreen)
                Spacer()
                Button(L10n.t("clear")) {
                    vm.clearAll(); focusedCode = nil
                }
                .font(.caption).foregroundColor(.brandBlue)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
        .readableWidth()
        .background(Color.white)
    }

    private var currencyList: some View {
        // Не List: он перехватывает drag & drop и отдаёт перестановку только
        // своему .onMove, который работает лишь в режиме редактирования.
        // На LazyVStack перетаскивание за ручку работает сразу, как в Android.
        ScrollView {
            LazyVStack(spacing: 8) {
                ForEach(vm.currencies, id: \.self) { code in
                    CurrencyRowView(vm: vm, code: code, focusedCode: $focusedCode,
                                    dragged: $dragged, onDelete: { deleteCandidate = code })
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
        }
        .readableWidth()
        .background(
            // тап по фону — ещё один способ убрать клавиатуру
            Color.bgGray.onTapGesture { focusedCode = nil }
        )
    }

    private var footer: some View {
        VStack(spacing: 2) {
            HStack {
                Button("＋ " + L10n.t("add_currency").replacingOccurrences(of: "＋ ", with: "")) {
                    showAdd = true
                }
                .font(.subheadline).foregroundColor(.brandBlue)
                Spacer()
                Button(L10n.t("mid_market")) { showInfo = true }
                    .font(.caption).foregroundColor(.brandBlue)
            }
            HStack {
                Link(L10n.t("attribution"),
                     destination: URL(string: "https://www.exchangerate-api.com")!)
                    .font(.caption2).foregroundColor(Color(hex: "B0BEC5"))
                Spacer()
                Text(L10n.t("version", AppInfo.version))
                    .font(.caption2).foregroundColor(Color(hex: "BBBBBB"))
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .readableWidth()
        .background(Color.white)
    }
}
