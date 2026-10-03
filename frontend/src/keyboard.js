export function handleTabListKeydown(event, tabIds, selectedIndex, selectIndex) {
  let nextIndex
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    nextIndex = (selectedIndex + 1) % tabIds.length
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    nextIndex = (selectedIndex - 1 + tabIds.length) % tabIds.length
  } else if (event.key === 'Home') {
    nextIndex = 0
  } else if (event.key === 'End') {
    nextIndex = tabIds.length - 1
  } else {
    return
  }

  event.preventDefault()
  selectIndex(nextIndex)
  document.getElementById(tabIds[nextIndex])?.focus()
}
