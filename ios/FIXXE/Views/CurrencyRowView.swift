import SwiftUI
import UniformTypeIdentifiers

struct CurrencyRowView: View {
    @ObservedObject var vm: ConverterViewModel
    let code: String
    var focusedCode: FocusState<String?>.Binding
    @Binding var dragged: String?
    let onDelete: () -> Void

    private var isActive: Bool { vm.activeCode == code }

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 8) {
                Button(action: onDelete) {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Color(hex: "C4C9D0"))
                        .frame(width: 26, height: 30)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("delete-\(code)")
                .accessibilityLabel(L10n.t("delete"))
                FlagView(code: code, size: 24)
                Text(code).font(.subheadline.bold())
                Spacer()
                Text(CurrencyData.symbol(code))
                    .font(.footnote).foregroundColor(.gray)
                amountField
                dragHandle
            }
            HStack {
                Text(CurrencyData.displayName(code) ?? "")
                    .font(.caption2).foregroundColor(.secondary)
                Spacer()
                Text(vm.rateLine(code))
                    .font(.caption2).foregroundColor(Color(hex: "AAAAAA"))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Color.white)
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10)
            .stroke(isActive ? Color.brandBlue : .clear, lineWidth: 2))
        .contentShape(Rectangle())
        .onDrop(of: [UTType.text], delegate: RowDropDelegate(target: code, dragged: $dragged, vm: vm))
    }

    /// Ручка перетаскивания — отдельная зона, как в Android-версии.
    /// `.onMove` в SwiftUI требует режима редактирования, поэтому порядок
    /// меняем через drag & drop, а тащим строго за этот значок, чтобы жест
    /// не мешал ни вводу суммы, ни прокрутке списка.
    private var dragHandle: some View {
        Image(systemName: "line.3.horizontal")
            .font(.system(size: 15, weight: .regular))
            .foregroundColor(Color(hex: "C4C9D0"))
            .frame(width: 28, height: 34)
            .contentShape(Rectangle())
            .accessibilityIdentifier("drag-\(code)")
            .accessibilityLabel(L10n.t("reorder"))
            .onDrag {
                dragged = code
                focusedCode.wrappedValue = nil      // клавиатура мешает перетаскиванию
                return NSItemProvider(object: code as NSString)
            }
    }

    @ViewBuilder private var amountField: some View {
        if isActive {
            TextField("0", text: Binding(
                get: { vm.inputText },
                set: { vm.inputChanged($0) }))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .font(.title3.bold())
                .frame(width: 130)
                .focused(focusedCode, equals: code)
                .accessibilityIdentifier("amount-\(code)")
                // на цифровой клавиатуре iOS нет кнопки «Готово» — добавляем свою,
                // иначе клавиатуру нечем убрать и она закрывает нижние строки
                .toolbar {
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button { focusedCode.wrappedValue = nil } label: {
                            Image(systemName: "keyboard.chevron.compact.down")
                        }
                        .accessibilityLabel(L10n.t("close"))
                        .accessibilityIdentifier("dismiss-keyboard")
                    }
                }
        } else {
            Text(SmartFormat.amount(vm.converted(code), decimals: vm.decimals))
                .font(.title3.bold())
                .frame(width: 130, alignment: .trailing)
                .accessibilityIdentifier("amount-\(code)")
                .onTapGesture {
                    vm.setActive(code)
                    focusedCode.wrappedValue = code
                }
        }
    }
}
